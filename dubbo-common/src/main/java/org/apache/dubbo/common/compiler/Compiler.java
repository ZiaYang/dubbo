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
package org.apache.dubbo.common.compiler;

import org.apache.dubbo.common.extension.SPI;

/**
 * Compiler. (SPI, Singleton, ThreadSafe)
 *
 * javassist为默认编译器
 * 如果用户想改变默认编译器，则可以通过<dubbo:application compiler="jdk" />标签进行配置
 *
 * Dubbo SPI的自适应特性让整个框架非常灵活，而动态编译又是自适应特性的基础。
 * 因为动态生成的自适应类只是字符串 ，需要通过编译才能得到真正的Class0虽然我们可以使用反射 来动态代理一个类，
 * 但是在性能上和直接编译好的Class会有一定的差距。
 * Dubbo SPI通过代码 的动态生成，并配合动态编译器，灵活地在原始类基础上创建新的自适应类。
 */
@SPI("javassist")
public interface Compiler {

    /**
     * Compile java source code.
     *
     * @param code        Java source code
     * @param classLoader classloader
     * @return Compiled class
     */
    Class<?> compile(String code, ClassLoader classLoader);

}
