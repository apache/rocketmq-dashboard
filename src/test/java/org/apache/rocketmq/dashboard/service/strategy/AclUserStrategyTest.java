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
package org.apache.rocketmq.dashboard.service.strategy;

import java.util.HashMap;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AclUserStrategyTest {

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private ClusterInfoService clusterInfoService;

    private AclUserStrategy strategy;

    @Before
    public void setUp() {
        strategy = new AclUserStrategy(mqAdminExt, clusterInfoService);
    }

    private ClusterInfo clusterWithBroker(String brokerName, String addr) {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName(brokerName);
        HashMap<Long, String> addrs = new HashMap<>();
        if (addr != null) {
            addrs.put(0L, addr);
        }
        brokerData.setBrokerAddrs(addrs);
        brokerAddrTable.put(brokerName, brokerData);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    @Test
    public void testReturnsNullWhenClusterInfoMissing() throws Exception {
        when(clusterInfoService.get()).thenReturn(null);
        assertNull(strategy.getUserInfoByUsername("admin"));
        verify(mqAdminExt, never()).getUser(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void testReturnsNullWhenBrokerTableEmpty() {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(new HashMap<>());
        when(clusterInfoService.get()).thenReturn(clusterInfo);
        assertNull(strategy.getUserInfoByUsername("admin"));
    }

    @Test
    public void testSkipsBrokerWithoutAddrs() {
        when(clusterInfoService.get()).thenReturn(clusterWithBroker("broker-a", null));
        assertNull(strategy.getUserInfoByUsername("admin"));
    }

    @Test
    public void testReturnsUserFromBroker() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBroker("broker-a", "127.0.0.1:10911"));
        UserInfo expected = UserInfo.of("admin", "secret", "super");
        when(mqAdminExt.getUser("127.0.0.1:10911", "admin")).thenReturn(expected);

        assertEquals(expected, strategy.getUserInfoByUsername("admin"));
    }

    @Test
    public void testBrokerFailureFallsThroughToNull() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBroker("broker-a", "127.0.0.1:10911"));
        when(mqAdminExt.getUser("127.0.0.1:10911", "admin")).thenThrow(new RuntimeException("acl disabled"));

        assertNull(strategy.getUserInfoByUsername("admin"));
    }

    @Test
    public void testUserNotFoundOnAnyBroker() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBroker("broker-a", "127.0.0.1:10911"));
        when(mqAdminExt.getUser("127.0.0.1:10911", "ghost")).thenReturn(null);

        assertNull(strategy.getUserInfoByUsername("ghost"));
    }
}
