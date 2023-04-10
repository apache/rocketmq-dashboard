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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MQAdminPoolTest {

    private MqAdminExtObjectPool mqAdminExtObjectPool;

    private GenericObjectPool<MQAdminExt> pool;

    private MQAdminPooledObjectFactory mqAdminPooledObjectFactory;

    @BeforeEach
    public void init() {
        mqAdminExtObjectPool = new MqAdminExtObjectPool();
        RMQConfigure rmqConfigure = mock(RMQConfigure.class);
        when(rmqConfigure.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(rmqConfigure.getAccessKey()).thenReturn("rocketmq");
        when(rmqConfigure.getSecretKey()).thenReturn("12345678");
        pool = mqAdminExtObjectPool.mqAdminExtPool(rmqConfigure);
        mqAdminPooledObjectFactory = (MQAdminPooledObjectFactory) pool.getFactory();
    }

    @Test
    public void testMakeObject() throws Exception {
        PooledObject<MQAdminExt> mqAdmin = mqAdminPooledObjectFactory.makeObject();
        Assertions.assertNotNull(mqAdmin);
    }

    @Test
    public void testDestroyObject() {
        PooledObject<MQAdminExt> mqAdmin = spy(new DefaultPooledObject<MQAdminExt>(null));
        Assertions.assertNotNull(mqAdmin);
        MQAdminExt mqAdminExt = mock(MQAdminExt.class);
        doNothing().doThrow(new RuntimeException("shutdown exception")).when(mqAdminExt).shutdown();
        when(mqAdmin.getObject()).thenReturn(mqAdminExt);
        // shutdown
        assertDoesNotThrow(() -> mqAdminPooledObjectFactory.destroyObject(mqAdmin));
        // exception
        assertThrows(RuntimeException.class, () -> mqAdminPooledObjectFactory.destroyObject(mqAdmin), "shutdown exception");
    }

    @Test
    public void testValidateObject() throws Exception {
        PooledObject<MQAdminExt> mqAdmin = spy(new DefaultPooledObject<MQAdminExt>(null));
        Assertions.assertNotNull(mqAdmin);
        MQAdminExt mqAdminExt = mock(MQAdminExt.class);
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        clusterInfo.getBrokerAddrTable().clear();
        when(mqAdminExt.examineBrokerClusterInfo())
            .thenThrow(new RuntimeException("examineBrokerClusterInfo exception"))
            .thenReturn(null)
            .thenReturn(new ClusterInfo())
            .thenReturn(clusterInfo)
            .thenReturn(MockObjectUtil.createClusterInfo());
        when(mqAdmin.getObject()).thenReturn(mqAdminExt);
        // exception
        Assertions.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // clusterInfo == null
        Assertions.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // clusterInfo.getBrokerAddrTable() == null
        Assertions.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // clusterInfo.getBrokerAddrTable().size() <= 0
        Assertions.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // pass validate
        Assertions.assertTrue(mqAdminPooledObjectFactory.validateObject(mqAdmin));

    }
}
