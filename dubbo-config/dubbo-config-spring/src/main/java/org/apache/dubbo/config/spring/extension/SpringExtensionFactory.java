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
package org.apache.dubbo.config.spring.extension;

import org.apache.dubbo.common.extension.ExtensionFactory;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;

import com.alibaba.spring.util.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Set;

/**
 * SpringExtensionFactory
 *
 * 除了可以从Dubbo SPI管理的容器中获取扩展点实例，还可以从Spring容器中获取。
 *
 * 那么Dubbo和Spring容器之间是如何打通的呢 ？
 *
 * 我们先来看SpringExtensionFactory的实现，该工厂提供了保存Spring上下文的静态方法，可以把Spring上下文保存到Set集合中。
 * 当调用getExtension获取扩展类时，会遍历Set集合中所有的Spring上下文，先根据名字依次
 * 从每个Spring容器中进行匹配，如果根据名字没匹配到，则根据类型去匹配 ，如果还没匹配到 则返回null。
 *
 * 那么Spring的上下文又是在什么时候被保存起来的呢？我们可以通过代码搜索得知，在
 * ReferenceBean和ServiceBean中会调用静态方法保存 Spring上下文，即一个服务被发布或被
 * 引用的时候，对应的Spring上下文会被保存下来。
 */
public class SpringExtensionFactory implements ExtensionFactory {
    private static final Logger logger = LoggerFactory.getLogger(SpringExtensionFactory.class);

    //保存Spring上下文的Set集合
    private static final Set<ApplicationContext> CONTEXTS = new ConcurrentHashSet<ApplicationContext>();

    public static void addApplicationContext(ApplicationContext context) {
        CONTEXTS.add(context);
        if (context instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) context).registerShutdownHook();
        }
    }

    public static void removeApplicationContext(ApplicationContext context) {
        CONTEXTS.remove(context);
    }

    public static Set<ApplicationContext> getContexts() {
        return CONTEXTS;
    }

    // currently for test purpose
    public static void clearContexts() {
        CONTEXTS.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getExtension(Class<T> type, String name) {

        //SPI should be get from SpiExtensionFactory。
        if (type.isInterface() && type.isAnnotationPresent(SPI.class)) {
            return null;
        }

        // 遍历所有Spring上下文，先根据名字从Spring容器中查找，如果名字没找到，就直接通过类型查找。
        for (ApplicationContext context : CONTEXTS) {
            //Spring容器中的依赖查找
            T bean = BeanFactoryUtils.getOptionalBean(context, name, type);
            if (bean != null) {
                return bean;
            }
        }

        logger.warn("No spring extension (bean) named:" + name + ", try to find an extension (bean) of type " + type.getName());

        return null;
    }
}
