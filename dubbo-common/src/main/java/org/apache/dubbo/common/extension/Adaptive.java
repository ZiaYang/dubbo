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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provide helpful information for {@link ExtensionLoader} to inject dependency extension instance.
 *
 * 扩展点自适应注解。 可以根据传入URL动态确定要使用的具体实现类（实现类可能有很多个），从而解决自动加载过程中的实例注入问题。
 *
 * Adaptive注解可以标记在类、接口、枚举类和方法上。
 * 但是在整个Dubbo框架中，只有几个地方使用在类级别上，如AdaptiveExtensionFactory和AdaptiveCompiler。
 * 其余都标注在 方法上。
 * 如果标注在接口的方法上 ，即方法级别注解，则可以通过参数动态获得实现类，这一点已经在4.1.5节的自适应特性上说明。
 * 方法级别注解在第一次 getExtension时，会自动生成 和编译一个动态的Adaptive类，从而达到动态实现类的效果 。
 *
 * 在扩展点接口的多个实现里，只能有一个实现上可以加@Adaptive注解。如果多个 实现类都有该注解，则会抛出异常：More than 1 adaptive class found0
 *
 * 为什么有些实现类上会标注©Adaptive呢？放在实现类上，主要是为了直接固定对应的实现而不需要动态生成代码实现 ，就像策略模式直接确定实现类 。
 * 在 ExtensionLoader中会缓存两个与©Adaptive有关的对象，一个缓存在cachedAdaptiveClass中，
 * 即Adaptive具体实现类的Class类型；另外一个缓存在cachedAdaptivelnstance中，即Class 的具体实例化对象 。在扩展点初始化时，如果发现实现类有 @Adaptive注解，则直接赋值给
 * cachedAdaptiveClass ,后续实例化类的时候，就不会再动态生成代码，直接实例化 cachedAdaptiveClass,并把实例缓存到cachedAdaptivelnstance中。如果注解在接口方法上，
 * 则会根据参数，动态获得扩展点的实现 ，会动态生成Adaptive类，再缓存到 cachedAdaptivelnstance 中。
 *
 * @see ExtensionLoader
 * @see URL
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Adaptive {
    /**
     * Decide which target extension to be injected. The name of the target extension is decided by the parameter passed
     * in the URL, and the parameter names are given by this method.
     * <p>
     * If the specified parameters are not found from {@link URL}, then the default extension will be used for
     * dependency injection (specified in its interface's {@link SPI}).
     * <p>
     * For example, given <code>String[] {"key1", "key2"}</code>:
     * <ol>
     * <li>find parameter 'key1' in URL, use its value as the extension's name</li>
     * <li>try 'key2' for extension's name if 'key1' is not found (or its value is empty) in URL</li>
     * <li>use default extension if 'key2' doesn't exist either</li>
     * <li>otherwise, throw {@link IllegalStateException}</li>
     * </ol>
     * If the parameter names are empty, then a default parameter name is generated from interface's
     * class name with the rule: divide classname from capital char into several parts, and separate the parts with
     * dot '.', for example, for {@code org.apache.dubbo.xxx.YyyInvokerWrapper}, the generated name is
     * <code>String[] {"yyy.invoker.wrapper"}</code>.
     *
     * @return parameter names in URL
     *
     *
     * Adaptive可 以传入多个key值，在初始化Adaptive注解的接口时，会先对传入的URL进行key值匹配。
     * 第一个key没匹配上则匹配第二个，以此类推。直到所有的key匹配完毕，如果还没有匹配到， 则会使用“驼峰规则”匹配。
     * 如果也没匹配到，则会抛出IllegalStateException异常。
     */
    // 数组，可以设置多个key,会按顺序依次匹配.
    String[] value() default {};

}