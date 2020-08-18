/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;

/**
 * ConsistentHashLoadBalance
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";

    /**
     * Hash nodes name
     */
    public static final String HASH_NODES = "hash.nodes";

    /**
     * Hash arguments name
     */
    public static final String HASH_ARGUMENTS = "hash.arguments";

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    /**
     * 一致性Hash,相同参数的请求总是发到同一提供者。
     * 一致性hash环+虚拟哈希槽位算法，决定请求落在哪个节点上，当有节点新增或减少时，能平均分配请求到其他节点上。
     *
     * 普通一致性Hash会把每个服务节点散列到环形上，然后把请求的客户端散列到环上，顺时针往前找到的第一个节点就是要调用的节点。
     * 假设客户端落在区域2,则顺时针找到的服务C就是要调用的节点。当服务C宕机下线，则落在区域2部分的客户端会自动迁移到服务D上。 这样就避免了全部重新散列的问题。
     * 普通的一致性Hash也有一定的局限性 ，它的散列不一定均匀，容易造成某些节点压力大。
     * 因此Dubbo框架使用了优化过的Ketama一致性Hash。
     * 这种算法会为每个真实节点再创建多个虚拟节点，让节点在环形上的分布更加均匀 ，后续的调用也会随之更加均匀 。
     *
     *
     * 当某一台提供者 “挂”时，原本发往该提供者的请求，基于虚拟节点，会平摊到其他提 供者，不会引起剧烈变动。
     * 默认只对第一个参数“Hash”，如果要修改， 则配置 <dubbo:parameter key="hash.arguments" value="0,1"/>默认使用160份虚拟节点，
     * 如果要修改，则配置〈dubbo:parameter key="hash.nodes" value="320" />
     *
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        //拼接出key
        String methodName = RpcUtils.getMethodName(invocation);
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        // using the hashcode of list to compute the hash only pay attention to the elements in the list
        int invokersHashCode = invokers.hashCode();

        //找到接口方法对应的哈希选择器
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);

        //现在Invoker列表的hash码和之前的不一样，说明Invoker列表已经发生了变化，则重新创建selector
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        //selector选出一个invoker
        return selector.select(invocation);
    }

    private static final class ConsistentHashSelector<T> {

        //Hash环。 TreeMap实现，所有真实、虚拟节点都会放入TreeMap。
        private final TreeMap<Long, Invoker<T>> virtualInvokers;

        private final int replicaNumber;

        private final int identityHashCode;

        private final int[] argumentIndex;

        /**
         * 节点的IP+递增数字做MD5，以此作为节点标识
         *
         * 在客户端调用时候 ，只要对请求的参数也做“MD5”即可。
         * 虽然此时得到的MD5值不一定能对应到 TreeMap中的一个key,因为每次的请求参数不同。
         * 但是由于TreeMap是有序的树形结构，所以我们可以调用TreeMap的ceilingEntry方法，用于返回一个至少大于或等于当前给定 key的Entry,从而达到顺时针往前找的效果。
         * 如果找不到，说明当前请求的key比较大，按照换新模型，则使用第一个节点，则使用firstEntry返回第一个节点。
         *
         * @param invokers
         * @param methodName
         * @param identityHashCode
         */
        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            //replicaNumber虚拟节点数量。默认一个实体节点拥有虚拟节点160个
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);

            //解析hash Index参数
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            //遍历所有的节点
            for (Invoker<T> invoker : invokers) {
                //拿到每个invoker的IP地址
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < replicaNumber / 4; i++) {
                    //ip + i 生成md5
                    byte[] digest = md5(address + i);
                    for (int h = 0; h < 4; h++) {
                        //对标识做hash得到treemap的key，以invoker为value
                        long m = hash(digest, h);
                        //设置虚拟节点
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }

        public Invoker<T> select(Invocation invocation) {
            //根据调用参数生成key
            String key = toKey(invocation.getArguments());
            byte[] digest = md5(key);
            return selectForKey(hash(digest, 0));
        }

        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            //ceilingEntry，用于返回一个至少大于或等于当前给定key的entry
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }

    }

}
