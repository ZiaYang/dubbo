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
package org.apache.dubbo.common.extension.factory;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AdaptiveExtensionFactory
 * 类上标注Adaptive注解，那么该类会作为默认实现，无需动态生成代码。
 *
 * 被AdaptiveExtensionFactory缓存的工厂会通过TreeSet进行排序，SPI排在前面，Spring 排在后面。
 * 当调用getExtension方法时，会遍历所有的工厂，先从SPI容器中获取扩展类；
 * 如果没找到，则再从Spring容器中查找。
 * 我们可以理解为，AdaptiveExtensionFactory持有了所有的具体工厂实现，它的getExtension方法中只是遍历了它持有的所有工厂。
 * 最终还是调用SPI或Spring工厂实现的getExtension方法。
 */
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {

    //扩展工厂类缓存集合
    private final List<ExtensionFactory> factories;

    // 获取所有扩展类工厂并缓存起来，。SPIExtensionFactory、SpringExtensionFactory。
    public AdaptiveExtensionFactory() {
        ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
        List<ExtensionFactory> list = new ArrayList<ExtensionFactory>();

        // 遍历所有的工厂名称，获取对应的工厂，并保存。
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        factories = Collections.unmodifiableList(list);
    }

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // SPI -> Spring
        for (ExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }

}
