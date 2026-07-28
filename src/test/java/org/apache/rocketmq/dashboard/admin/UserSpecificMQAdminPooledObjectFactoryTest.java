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

import java.util.HashMap;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserSpecificMQAdminPooledObjectFactoryTest {

    @Mock
    private MQAdminExt mqAdminExt;

    private ClientConfig baseClientConfig;

    @Before
    public void setUp() {
        baseClientConfig = new ClientConfig();
        baseClientConfig.setNamesrvAddr("127.0.0.1:9876");
    }

    @Test
    public void testConstructorWithCredentialsCreatesAclHook() {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        Object rpcHook = ReflectionTestUtils.getField(factory, "rpcHook");
        assertNotNull(rpcHook);
        assertTrue(rpcHook instanceof AclClientRPCHook);
    }

    @Test
    public void testConstructorWithoutCredentialsHasNoHook() {
        UserSpecificMQAdminPooledObjectFactory emptyAk =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "", "sk");
        assertNull(ReflectionTestUtils.getField(emptyAk, "rpcHook"));

        UserSpecificMQAdminPooledObjectFactory nullSk =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", null);
        assertNull(ReflectionTestUtils.getField(nullSk, "rpcHook"));
    }

    @Test
    public void testDestroyObjectShutsDownAdmin() {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        factory.destroyObject(new DefaultPooledObject<>(mqAdminExt));
        verify(mqAdminExt).shutdown();
    }

    @Test
    public void testDestroyObjectSwallowsShutdownFailure() {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        doThrow(new RuntimeException("shutdown failed")).when(mqAdminExt).shutdown();
        factory.destroyObject(new DefaultPooledObject<>(mqAdminExt));
        verify(mqAdminExt).shutdown();
    }

    @Test
    public void testDestroyObjectWithNullAdmin() {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        factory.destroyObject(new DefaultPooledObject<>(null));
    }

    @Test
    public void testValidateObjectWithNullAdmin() {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        assertFalse(factory.validateObject(new DefaultPooledObject<>(null)));
    }

    @Test
    public void testValidateObjectWithBrokerReturnsTrue() throws Exception {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", new BrokerData());
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        assertTrue(factory.validateObject(new DefaultPooledObject<>(mqAdminExt)));
    }

    @Test
    public void testValidateObjectWithEmptyBrokerTableReturnsFalse() throws Exception {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(new HashMap<>());
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        assertFalse(factory.validateObject(new DefaultPooledObject<>(mqAdminExt)));
    }

    @Test
    public void testValidateObjectWithNullClusterInfoReturnsFalse() throws Exception {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(null);

        assertFalse(factory.validateObject(new DefaultPooledObject<>(mqAdminExt)));
    }

    @Test
    public void testValidateObjectOnExceptionReturnsFalse() throws Exception {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        when(mqAdminExt.examineBrokerClusterInfo()).thenThrow(new RuntimeException("network down"));

        assertFalse(factory.validateObject(new DefaultPooledObject<>(mqAdminExt)));
    }

    @Test
    public void testActivateAndPassivateObjectAreNoOps() {
        UserSpecificMQAdminPooledObjectFactory factory =
            new UserSpecificMQAdminPooledObjectFactory(baseClientConfig, "ak", "sk");
        PooledObject<MQAdminExt> pooled = new DefaultPooledObject<>(mqAdminExt);
        factory.activateObject(pooled);
        factory.passivateObject(pooled);
    }
}
