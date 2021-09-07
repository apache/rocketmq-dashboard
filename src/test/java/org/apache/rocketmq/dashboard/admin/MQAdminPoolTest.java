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

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MQAdminPoolTest {

    private MqAdminExtObjectPool mqAdminExtObjectPool;

    private GenericObjectPool<MQAdminExt> pool;

    private MQAdminPooledObjectFactory mqAdminPooledObjectFactory;

    @Before
    public void init() {
        mqAdminExtObjectPool = new MqAdminExtObjectPool();
        RMQConfigure rmqConfigure = mock(RMQConfigure.class);
        when(rmqConfigure.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(rmqConfigure.getAccessKey()).thenReturn("rocketmq");
        when(rmqConfigure.getSecretKey()).thenReturn("12345678");
        ReflectionTestUtils.setField(mqAdminExtObjectPool, "rmqConfigure", rmqConfigure);
        pool = mqAdminExtObjectPool.mqAdminExtPool();
        mqAdminPooledObjectFactory = (MQAdminPooledObjectFactory) pool.getFactory();
    }

    @Test
    public void testMakeObject() throws Exception {
        PooledObject<MQAdminExt> mqAdmin = mqAdminPooledObjectFactory.makeObject();
        Assert.assertNotNull(mqAdmin);
    }

    @Test
    public void testDestroyObject() {
        PooledObject<MQAdminExt> mqAdmin = mock(PooledObject.class);
        Assert.assertNotNull(mqAdmin);
        MQAdminExt mqAdminExt = mock(MQAdminExt.class);
        doNothing().doThrow(new RuntimeException("shutdown exception")).when(mqAdminExt).shutdown();
        when(mqAdmin.getObject()).thenReturn(mqAdminExt);
        // shutdown
        mqAdminPooledObjectFactory.destroyObject(mqAdmin);
        // exception
        mqAdminPooledObjectFactory.destroyObject(mqAdmin);
    }

    @Test
    public void testValidateObject() throws Exception {
        PooledObject<MQAdminExt> mqAdmin = mock(PooledObject.class);
        Assert.assertNotNull(mqAdmin);
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
        Assert.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // clusterInfo == null
        Assert.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // clusterInfo.getBrokerAddrTable() == null
        Assert.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // clusterInfo.getBrokerAddrTable().size() <= 0
        Assert.assertFalse(mqAdminPooledObjectFactory.validateObject(mqAdmin));
        // pass validate
        Assert.assertTrue(mqAdminPooledObjectFactory.validateObject(mqAdmin));

    }
}
