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

package org.apache.rocketmq.dashboard.admin;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.aspect.admin.MQAdminAspect;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MQAdminAspectTest {

    @Mock
    private RMQConfigure rmqConfigure;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(rmqConfigure.isLoginRequired()).thenReturn(false);
    }

    @Test
    public void testAroundMQAdminMethod() throws Throwable {
        MQAdminAspect mqAdminAspect = new MQAdminAspect();
        Field field = mqAdminAspect.getClass().getDeclaredField("rmqConfigure");
        field.setAccessible(true);
        field.set(mqAdminAspect, rmqConfigure);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = mock(Method.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        GenericObjectPool<MQAdminExt> mqAdminExtPool = mock(GenericObjectPool.class);
        // 1. Mock borrowObject() 行为：第一次抛异常，第二次返回 DefaultMQAdminExt
        when(mqAdminExtPool.borrowObject())
                .thenThrow(new RuntimeException("borrowObject exception"))
                .thenReturn(new DefaultMQAdminExt());

        // 2. Mock returnObject() 行为：第一次什么都不做，第二次抛异常
        doNothing().when(mqAdminExtPool).returnObject(any());
        doThrow(new RuntimeException("returnObject exception"))
                .when(mqAdminExtPool).returnObject(any());

        // 3. 通过反射注入 Mock 对象
        field = mqAdminAspect.getClass().getDeclaredField("mqAdminExtPool");
        field.setAccessible(true);
        field.set(mqAdminAspect, mqAdminExtPool);

        // 4. 第一次调用 aroundMQAdminMethod，预期 borrowObject() 抛异常
        try {
            mqAdminAspect.aroundMQAdminMethod(joinPoint);
            fail("Expected RuntimeException but no exception was thrown");
        } catch (RuntimeException e) {
            assertEquals("borrowObject exception", e.getMessage());
        }

        // 5. 第二次调用 aroundMQAdminMethod，预期 borrowObject() 成功，但 returnObject() 抛异常
        try {
            mqAdminAspect.aroundMQAdminMethod(joinPoint);
            fail("Expected RuntimeException but no exception was thrown");
        } catch (RuntimeException e) {
            assertEquals("returnObject exception", e.getMessage());
        }

        // 6. 验证 borrowObject() 和 returnObject() 各调用了两次
        verify(mqAdminExtPool, times(2)).borrowObject();
        verify(mqAdminExtPool, times(1)).returnObject(any());
    }
}
