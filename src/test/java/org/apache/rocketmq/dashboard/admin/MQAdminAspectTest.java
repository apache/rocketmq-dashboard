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
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.aspect.admin.MQAdminAspect;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MQAdminAspectTest {

    @Test
    public void testAroundMQAdminMethod() throws Throwable {
        MQAdminAspect mqAdminAspect = new MQAdminAspect();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = mock(Method.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        GenericObjectPool<MQAdminExt> mqAdminExtPool = mock(GenericObjectPool.class);
        when(mqAdminExtPool.borrowObject())
            .thenThrow(new RuntimeException("borrowObject exception"))
            .thenReturn(new DefaultMQAdminExt());
        doNothing().doThrow(new RuntimeException("returnObject exception"))
            .when(mqAdminExtPool).returnObject(any());
        Field field = mqAdminAspect.getClass().getDeclaredField("mqAdminExtPool");
        field.setAccessible(true);
        field.set(mqAdminAspect, mqAdminExtPool);
        // exception
        mqAdminAspect.aroundMQAdminMethod(joinPoint);
        mqAdminAspect.aroundMQAdminMethod(joinPoint);
    }
}
