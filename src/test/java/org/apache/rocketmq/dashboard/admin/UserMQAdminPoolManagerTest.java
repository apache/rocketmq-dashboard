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

import jakarta.annotation.PreDestroy;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserMQAdminPoolManagerTest {

    private UserMQAdminPoolManager poolManager;

    @Before
    public void setUp() {
        RMQConfigure configure = mock(RMQConfigure.class);
        when(configure.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(configure.getClientCallbackExecutorThreads()).thenReturn(4);
        when(configure.getIsVIPChannel()).thenReturn("false");
        poolManager = new UserMQAdminPoolManager(configure);
    }

    @Test
    public void testShutdownAllPoolsUsesJakartaPreDestroy() throws Exception {
        Method shutdownMethod = UserMQAdminPoolManager.class.getMethod("shutdownAllPools");

        Assert.assertNotNull(shutdownMethod.getAnnotation(PreDestroy.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testShutdownAllPoolsClosesAndRemovesPools() {
        GenericObjectPool<MQAdminExt> pool = mock(GenericObjectPool.class);
        ConcurrentMap<String, GenericObjectPool<MQAdminExt>> userPools =
                (ConcurrentMap<String, GenericObjectPool<MQAdminExt>>) ReflectionTestUtils
                        .getField(poolManager, "userPools");
        Assert.assertNotNull(userPools);
        userPools.put("user-a", pool);

        poolManager.shutdownAllPools();

        verify(pool).close();
        Assert.assertTrue(userPools.isEmpty());
    }
}
