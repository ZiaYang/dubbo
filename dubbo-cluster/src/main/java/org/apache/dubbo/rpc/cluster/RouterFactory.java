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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * RouterFactory. (SPI, Singleton, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Routing">Routing</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 * @see org.apache.dubbo.rpc.cluster.Directory#list(org.apache.dubbo.rpc.Invocation)
 * <p>
 * Note Router has a different behaviour since 2.7.0, for each type of Router, there will only has one Router instance
 * for each service. See {@link CacheableRouterFactory} and {@link RouterChain} for how to extend a new Router or how
 * the Router instances are loaded.
 * 请注意在2.7.0版本后，路由有了不同的行为，对于每个类型的路由都只会有一个路由实例在每一个service中。
 *
 *
 * 路由分为条件路由、文件路由、脚本路由，对应dubbo-admin中三种不同的规则配置方式。
 * 条件路由是用户使用Dubbo定义的语法规则去写路由规则;
 * 文件路由则需要用户提交一个文件, 里面写着对应的路由规则，框架基于文件读取对应的规则；
 * 脚本路由则是使用JDK自身的脚本引擎解析路由规则脚本，所有JDK脚本引擎支持的脚本都能解析，默认是JavaScript
 */
@SPI
public interface RouterFactory {

    /**
     * Create router. 创建一个路由器
     * Since 2.7.0, most of the time, we will not use @Adaptive feature, so it's kept only for compatibility.
     *
     * @param url url
     * @return router instance
     */
    @Adaptive("protocol")
    Router getRouter(URL url);
}
