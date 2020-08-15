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
package org.apache.dubbo.common.compiler.support;

import org.apache.dubbo.common.compiler.Compiler;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * AdaptiveCompiler. (SPI, Singleton, ThreadSafe)
 *
 * Java中动态生成Class的方式有很多，可以直接基于字节码的方式生成 ，常见的工具库有 CGLIB、ASM> Javassist等。
 * 而自适应扩展点使用了生成字符串代码再编译为Class的方式。
 *
 * AdaptiveCompiler上面有^Adaptive注解，说明AdaptiveCompiler会固定为默认实现。
 * 这 个Compiler的主要作用和AdaptiveExtensionFactory相似，就是为了管理其他Compiler。
 *
 */
@Adaptive
public class AdaptiveCompiler implements Compiler {

    private static volatile String DEFAULT_COMPILER;

    /**
     * 设置默认的编译器名称
     * AdaptiveCompiler#setDefaultCompiler方法会在 ApplicationConfig 中被调用。
     * 也就是 Dubbo 在启动时，会解析配置中的<dubbo:application compiler="jdk" />标签，获取设置的值。
     * 初始化对应的编译器。
     * 如果没有标签设置，则使用@SPI("javassist")中的设置，即JavassistCmpiler
     */
    public static void setDefaultCompiler(String compiler) {
        DEFAULT_COMPILER = compiler;
    }

    @Override
    public Class<?> compile(String code, ClassLoader classLoader) {
        Compiler compiler;
        ExtensionLoader<Compiler> loader = ExtensionLoader.getExtensionLoader(Compiler.class);
        String name = DEFAULT_COMPILER; // copy reference
        if (name != null && name.length() > 0) {
            compiler = loader.getExtension(name);
        } else {
            compiler = loader.getDefaultExtension();
        }
        // 通过ExtensionLoader获取对应的编译器扩展类实现，并调用真正的compile做编译。
        return compiler.compile(code, classLoader);
    }

}
