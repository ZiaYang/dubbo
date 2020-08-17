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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.transport.dispatcher.all.AllDispatcher;

/**
 * ChannelHandlerWrapper (SPI, Singleton, ThreadSafe)
 * Dubbo中提供的线程池负责业务方法调用 ，我们称为业务线程，Dispatcher就是业务线程池派发器。
 *
 * 这里需要注意的是，Dispatcher真实的职责是创建具有线程派发能力的ChannelHandler。
 * 比如AllChannelHandler、MessageOnlyChannel、Handler和ExecutionChannelHandler等，其本身并不具备线程派发能力
 *
 * 具体业务方需要根据使用场景启用不同的策略。
 * 建议使用默认策略即可，如果在TCP连接 中需要做安全加密或校验，则可以使用ConnectionOrderedDispatcher策略。
 * 如果引入新的线程池，则不可避免地导致额外的线程切换，用户可在Dubbo配置中指定dispatcher属性让具 体策略生效。
 */
@SPI(AllDispatcher.NAME)
public interface Dispatcher {

    /**
     * dispatch the message to threadpool.
     *
     * @param handler
     * @param url
     * @return channel handler
     */
    @Adaptive({Constants.DISPATCHER_KEY, "dispather", "channel.handler"})
    // The last two parameters are reserved for compatibility with the old configuration
    ChannelHandler dispatch(ChannelHandler handler, URL url);

}
