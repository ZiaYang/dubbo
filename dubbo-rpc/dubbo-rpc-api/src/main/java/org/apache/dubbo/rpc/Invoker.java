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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.Node;

/**
 * Invoker. (API/SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.rpc.Protocol#refer(Class, org.apache.dubbo.common.URL)
 * @see org.apache.dubbo.rpc.InvokerListener
 * @see org.apache.dubbo.rpc.protocol.AbstractInvoker
 *
 * Invoker接口是最上层的接口，它下面分别有 AbstractClusterlnvoker、MockClusterlnvoker 和 MergeableClusterlnvoker 三个类。
 * 其中，AbstractClusterlnvoker 是一个抽象类，其封装了通用的模板逻辑，如获取服务列表 、负载均衡、调用服务提供者等，并预留了一个dolnvoke方法需要子类自行实现。
 * AbstractClusterInvoker下面有7个子类，分别实现了不同的集群容错机制。
 *
 * MockClusterlnvoker和MergeableClusterlnvoker由于并不适用于正常的集群容错逻辑，因此 没有挂在AbstractClusterlnvoker下面，而是直接继承了 Invoker接口。
 */
public interface Invoker<T> extends Node {

    /**
     * get service interface.
     *
     * @return service interface.
     */
    Class<T> getInterface();

    /**
     * invoke.
     *
     * @param invocation
     * @return result
     * @throws RpcException
     */
    Result invoke(Invocation invocation) throws RpcException;

}