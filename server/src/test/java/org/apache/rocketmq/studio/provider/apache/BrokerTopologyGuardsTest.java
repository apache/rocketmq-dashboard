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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerTopologyGuardsTest {

    @Test
    void knownBrokerEndpointsShouldIncludeResolvedIpForHostnameRegisteredBroker() throws Exception {
        ClusterInfo info = new ClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "localhost:10911");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a",
                new BrokerData("DefaultCluster", "broker-a", new HashMap<>(addrs)));
        info.setBrokerAddrTable(brokerAddrTable);

        MQAdminExt admin = mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);

        Set<String> endpoints = BrokerTopologyGuards.knownBrokerEndpoints(admin);

        String resolvedIp = InetAddress.getByName("localhost").getHostAddress();
        assertThat(endpoints).contains("localhost:10911");
        assertThat(endpoints).contains(resolvedIp + ":10911");
    }

    @Test
    void knownBrokerEndpointsShouldKeepPlainIpBrokerUnchanged() throws Exception {
        ClusterInfo info = new ClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.11:10911");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a",
                new BrokerData("DefaultCluster", "broker-a", new HashMap<>(addrs)));
        info.setBrokerAddrTable(brokerAddrTable);

        MQAdminExt admin = mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);

        Set<String> endpoints = BrokerTopologyGuards.knownBrokerEndpoints(admin);

        assertThat(endpoints).contains("10.0.0.11:10911");
    }
}
