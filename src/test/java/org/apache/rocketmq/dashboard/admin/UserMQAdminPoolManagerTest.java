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

import java.util.concurrent.ConcurrentMap;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserMQAdminPoolManagerTest {

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private GenericObjectPool<MQAdminExt> pool;

    @Mock
    private MQAdminExt mqAdminExt;

    private UserMQAdminPoolManager poolManager;

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, GenericObjectPool<MQAdminExt>> userPools() {
        return (ConcurrentMap<String, GenericObjectPool<MQAdminExt>>)
            ReflectionTestUtils.getField(poolManager, "userPools");
    }

    @Before
    public void setUp() {
        when(rmqConfigure.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(rmqConfigure.getClientCallbackExecutorThreads()).thenReturn(4);
        when(rmqConfigure.getIsVIPChannel()).thenReturn("false");
        when(rmqConfigure.isUseTLS()).thenReturn(false);
        poolManager = new UserMQAdminPoolManager(rmqConfigure);
    }

    @Test
    public void testBorrowMQAdminExtFromExistingPool() throws Exception {
        userPools().put("ak", pool);
        when(pool.borrowObject()).thenReturn(mqAdminExt);

        MQAdminExt borrowed = poolManager.borrowMQAdminExt("ak", "sk");
        assertSame(mqAdminExt, borrowed);
        verify(pool).borrowObject();
    }

    @Test
    public void testReturnMQAdminExtToExistingPool() {
        userPools().put("ak", pool);

        poolManager.returnMQAdminExt("ak", mqAdminExt);

        verify(pool).returnObject(mqAdminExt);
        verify(mqAdminExt, never()).shutdown();
    }

    @Test
    public void testReturnMQAdminExtFailureShutsDownObject() {
        userPools().put("ak", pool);
        doThrow(new IllegalStateException("returned object not currently part of this pool"))
            .when(pool).returnObject(mqAdminExt);

        poolManager.returnMQAdminExt("ak", mqAdminExt);

        verify(mqAdminExt).shutdown();
    }

    @Test
    public void testReturnMQAdminExtFailureAndShutdownFailureSwallowed() {
        userPools().put("ak", pool);
        doThrow(new IllegalStateException("return failed")).when(pool).returnObject(mqAdminExt);
        doThrow(new RuntimeException("shutdown failed")).when(mqAdminExt).shutdown();

        poolManager.returnMQAdminExt("ak", mqAdminExt);

        verify(mqAdminExt).shutdown();
    }

    @Test
    public void testReturnMQAdminExtWithoutPoolShutsDownObject() {
        poolManager.returnMQAdminExt("unknown", mqAdminExt);
        verify(mqAdminExt).shutdown();
    }

    @Test
    public void testReturnNullMQAdminExtWithoutPoolIsNoOp() {
        poolManager.returnMQAdminExt("unknown", null);
    }

    @Test
    public void testShutdownUserPoolClosesAndRemovesPool() {
        userPools().put("ak", pool);

        poolManager.shutdownUserPool("ak");

        verify(pool).close();
        assertTrue(userPools().isEmpty());
    }

    @Test
    public void testShutdownNonExistentUserPoolIsNoOp() {
        poolManager.shutdownUserPool("unknown");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testShutdownAllPoolsClosesEveryPool() {
        GenericObjectPool<MQAdminExt> secondPool = mock(GenericObjectPool.class);
        userPools().put("ak1", pool);
        userPools().put("ak2", secondPool);

        poolManager.shutdownAllPools();

        verify(pool).close();
        verify(secondPool).close();
        assertTrue(userPools().isEmpty());
    }
}
