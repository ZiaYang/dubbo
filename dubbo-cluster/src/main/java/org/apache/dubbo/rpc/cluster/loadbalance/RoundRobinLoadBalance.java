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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";
    
    private static final int RECYCLE_PERIOD = 60000;
    
    protected static class WeightedRoundRobin {
        private int weight; //Invoker设定的权重
        //考虑到并发场景下某个Invoker会被同时选中，表示该节点被所有线程选中的权重总和。例如：某个节点权重是100，被4个线程同时选中，则变为400.
        private AtomicLong current = new AtomicLong(0);
        private long lastUpdate;//最后一次更新的时间，用于后续缓存超时的判断。


        public int getWeight() {
            return weight;
        }
        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }
        public long increaseCurrent() {
            return current.addAndGet(weight);
        }
        public void sel(int total) {
            current.addAndGet(-1 * total);
        }
        public long getLastUpdate() {
            return lastUpdate;
        }
        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();
    private AtomicBoolean updateLock = new AtomicBoolean();
    
    /**
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     * 
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    /**
     *
     * 轮询 负载均衡，按公约后的权重设置轮询比例。
     * 存在慢的提供者累积请求的问题， 比如：第二台机器很慢，但没“挂”。
     * 当请求调到第二台时就卡在那里，久而久之，所有请求都卡在调到第二台上。
     *
     * 普通轮询会让每个节点获得的请求很均匀，如果某些节点的负载能力较弱，会堆积较多请求。
     * 当普通轮询无法满足需求，可以用权重进行干预。
     * 权重轮询分为 普通权重轮询、平滑权重轮询。
     * 普通权重轮询会造成某个节点会突然被频繁选中 ，这样很容易突然 让一个节点流量暴增。
     * Nginx中有一种叫平滑轮询的算法(smooth weighted round-robin balancing),这种算法在轮询时会穿插选择其他节点，让整个服务器选择的过程比较均匀，不会 “逮住”一个节点一直调用。
     * Dubbo框架中最新的RoundRobin代码已经改为平滑权重轮询算法
     *
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        //初始化权重缓存Map。invoker URL -> WeightedRoundRobin（封装了每个Invoker的权重）。并保存至全局的methodWeightMap缓存中。
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        Invoker<T> selectedInvoker = null;
        WeightedRoundRobin selectedWRR = null;

        //遍历所有Invoker，遍历过程中把每个Invoker的数据填充至Map中。
        for (Invoker<T> invoker : invokers) {
            String identifyString = invoker.getUrl().toIdentityString();
            //计算预热权重
            int weight = getWeight(invoker, invocation);
            WeightedRoundRobin weightedRoundRobin = map.get(identifyString);

            //没有weightRoundRobin则，创建新的，并设置权重。
            if (weightedRoundRobin == null) {
                weightedRoundRobin = new WeightedRoundRobin();
                weightedRoundRobin.setWeight(weight);
                map.putIfAbsent(identifyString, weightedRoundRobin);
                weightedRoundRobin = map.get(identifyString);
            }
            if (weight != weightedRoundRobin.getWeight()) {
                //weight changed 权重变更
                weightedRoundRobin.setWeight(weight);
            }
            //invoker的current = current + weight
            long cur = weightedRoundRobin.increaseCurrent();
            weightedRoundRobin.setLastUpdate(now);
            if (cur > maxCurrent) {
                //选择所有Invoker中Current最大的作为最终要调用的invoker
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight;
        }

        //检查invokers列表与权重缓存Map的大小是否相等，不等，说明Map中有无用数据，有些Invoker已经不存在了，Map中还未更新数据。
        if (!updateLock.get() && invokers.size() != map.size()) {
            if (updateLock.compareAndSet(false, true)) {
                try {
                    // copy -> modify -> update reference 复制、修改、更新引用 CopyOnWrite
                    ConcurrentMap<String, WeightedRoundRobin> newMap = new ConcurrentHashMap<>(map);
                    //根据lastUpdate清除新Map中的过期数据（默认60秒算过期）
                    newMap.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
                    methodWeightMap.put(key, newMap);
                } finally {
                    updateLock.set(false);
                }
            }
        }
        if (selectedInvoker != null) {
            //在返回之前，当前Invoker的current减去总权重。 这样下次当前invoker就不一定会再次被选中了。
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }
        // should not happen here 按理不应该走到这里的，但还是写了个。
        return invokers.get(0);
    }

}
