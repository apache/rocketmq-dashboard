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
package org.apache.rocketmq.dashboard.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MQAdminExtConfigure {

    @Bean
    MQAdminExt mqAdminExt(@Autowired GenericObjectPool<MQAdminExt> mqAdminExtPool) {

        Class<?>[] interfaces = new Class[] {MQAdminExt.class};
        return (MQAdminExt) Proxy.newProxyInstance(MQAdminExt.class.getClassLoader(), interfaces,
                new InvocationHandler() {

                    private final ConcurrentHashMap<Method, Method> cache = new ConcurrentHashMap<>();
        
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        MQAdminExt mqAdminExt = mqAdminExtPool.borrowObject();
                        try {
                            Method distMethod = cache.getOrDefault(method,
                                    mqAdminExt.getClass().getMethod(method.getName(), method.getParameterTypes()));
                            if (distMethod != null
                                    && distMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
                                cache.putIfAbsent(method, distMethod);
                                return distMethod.invoke(mqAdminExt, args);
                            } else {
                                throw new NotImplementedException(String.format(
                                        "Method named %s, with parameter type {%s} was not be implemented.",
                                        method.getName(), Arrays.toString(method.getParameterTypes())));
                            }
                        } finally {
                            mqAdminExtPool.returnObject(mqAdminExt);
                        }
                    }
                }
        );
    }

}
