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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.Node;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Directory. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Directory_service">Directory Service</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 *
 * 整个容错过程中首先会使用Directory#list来获取所有的Invoker列表。
 * Directory也有多种实现子类，既可以提供静态的Invoker列表，也可以提供动态的Invoker列表。
 * 静态列表是用户自己设置的Invoker列表；
 * 动态列表根据注册中心的数据动态变化，动态更新Invoker列表的数据，整个过程对上层透明
 *
 * 熟悉的“套路”，使用了模板模式。
 * Directory是顶层的接口。AbstractDirectory封装了通用的实现逻辑。
 * 抽象类包含RegistryDirectory和StaticDirectory两个子类。
 */
public interface Directory<T> extends Node {

    /**
     * get service type.
     *
     * @return service type.
     */
    Class<T> getInterface();

    /**
     * list invokers.
     * invokers列表
     *
     * Cluster会把拉取的服务列表聚合成一个Invoker,每次RPC调用前会通过Directory#list获取providers地址
     * （已经生成好的Invoker列表），获取这些服务列表给后续路由和负载均衡使用。
     * @return invokers
     */
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

    List<Invoker<T>> getAllInvokers();

    URL getConsumerUrl();

}