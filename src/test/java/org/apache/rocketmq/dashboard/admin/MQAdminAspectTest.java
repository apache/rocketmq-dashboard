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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.rocketmq.dashboard.aspect.admin.MQAdminAspect;
import org.apache.rocketmq.dashboard.aspect.admin.annotation.MultiMQAdminCmdMethod;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MQAdminAspectTest {

    @Test
    public void testAroundMQAdminMethod() throws Throwable {
        MQAdminAspect mqAdminAspect = new MQAdminAspect();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = mock(Method.class);
        MultiMQAdminCmdMethod annotationValue = mock(MultiMQAdminCmdMethod.class);
        when(annotationValue.timeoutMillis()).thenReturn(0L).thenReturn(3000L);
        when(method.getAnnotation(MultiMQAdminCmdMethod.class)).thenReturn(annotationValue);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        RMQConfigure rmqConfigure = mock(RMQConfigure.class);
        when(rmqConfigure.getAccessKey()).thenReturn("rocketmq");
        when(rmqConfigure.getSecretKey()).thenReturn("12345678");
        Field field = mqAdminAspect.getClass().getDeclaredField("rmqConfigure");
        field.setAccessible(true);
        field.set(mqAdminAspect, rmqConfigure);

        mqAdminAspect.aroundMQAdminMethod(joinPoint);
    }
}
