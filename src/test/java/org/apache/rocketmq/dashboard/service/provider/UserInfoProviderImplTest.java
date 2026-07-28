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
package org.apache.rocketmq.dashboard.service.provider;

import java.util.HashMap;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserInfoProviderImplTest {

    @InjectMocks
    private UserInfoProviderImpl provider;

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private ClusterInfoService clusterInfoService;

    private ClusterInfo clusterWithBrokers(String... addrs) {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        int index = 0;
        for (String addr : addrs) {
            BrokerData brokerData = new BrokerData();
            brokerData.setBrokerName("broker-" + index);
            HashMap<Long, String> brokerAddrs = new HashMap<>();
            if (addr != null) {
                brokerAddrs.put(0L, addr);
            }
            brokerData.setBrokerAddrs(brokerAddrs);
            brokerAddrTable.put("broker-" + index, brokerData);
            index++;
        }
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    @Test
    public void testReturnsNullWhenClusterInfoMissing() {
        when(clusterInfoService.get()).thenReturn(null);
        assertNull(provider.getUserInfoByUsername("admin"));
    }

    @Test
    public void testReturnsNullWhenBrokerTableEmpty() {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(new HashMap<>());
        when(clusterInfoService.get()).thenReturn(clusterInfo);
        assertNull(provider.getUserInfoByUsername("admin"));
    }

    @Test
    public void testReturnsUserFromFirstRespondingBroker() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBrokers("127.0.0.1:10911"));
        UserInfo expected = UserInfo.of("admin", "secret", "super");
        when(mqAdminExt.getUser("127.0.0.1:10911", "admin")).thenReturn(expected);

        assertEquals(expected, provider.getUserInfoByUsername("admin"));
    }

    @Test
    public void testSkipsBrokerWithoutPrimaryAddress() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBrokers((String) null));
        assertNull(provider.getUserInfoByUsername("admin"));
    }

    @Test
    public void testBrokerErrorFallsBackToNextBroker() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBrokers("127.0.0.1:10911", "127.0.0.1:10921"));
        UserInfo expected = UserInfo.of("admin", "secret", "normal");
        // One broker throws while the other returns the user; iteration order over
        // the HashMap is not guaranteed, so both are stubbed accordingly.
        when(mqAdminExt.getUser("127.0.0.1:10911", "admin")).thenThrow(new RuntimeException("timeout"));
        when(mqAdminExt.getUser("127.0.0.1:10921", "admin")).thenReturn(expected);

        assertEquals(expected, provider.getUserInfoByUsername("admin"));
    }

    @Test
    public void testReturnsNullWhenUserNotFoundAnywhere() throws Exception {
        when(clusterInfoService.get()).thenReturn(clusterWithBrokers("127.0.0.1:10911"));
        when(mqAdminExt.getUser("127.0.0.1:10911", "ghost")).thenReturn(null);

        assertNull(provider.getUserInfoByUsername("ghost"));
    }
}
