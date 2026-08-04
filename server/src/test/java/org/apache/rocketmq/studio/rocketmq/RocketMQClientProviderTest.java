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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQClientProviderTest {

    @Mock
    private ObjectProvider<DefaultMQAdminExt> adminExtProvider;

    @Mock
    private DefaultMQAdminExt adminExt;

    private RocketMQClientProvider provider;

    @BeforeEach
    void setUp() {
        when(adminExtProvider.getIfAvailable()).thenReturn(adminExt);
        provider = new RocketMQClientProvider(adminExtProvider);
    }

    @Test
    void producerScanTreatsNullTopicSetAsEmpty() throws Exception {
        when(adminExt.fetchAllTopicList()).thenReturn(new TopicList());

        List<ClientConnectionVO> connections = provider.findConnections("cluster-a", "Producer");

        assertThat(connections).isEmpty();
        verify(adminExt).fetchAllTopicList();
        verify(adminExt, never()).examineProducerConnectionInfo(anyString(), anyString());
    }

    @Test
    void consumerScanSkipsNullBrokerMetadata() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broken-broker", null);
        brokerAddrTable.put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "127.0.0.1:10911"))));
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        wrapper.setSubscriptionGroupTable(new ConcurrentHashMap<>());
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);

        List<ClientConnectionVO> connections = provider.findConnections("cluster-a", "Consumer");

        assertThat(connections).isEmpty();
        verify(adminExt).examineBrokerClusterInfo();
        verify(adminExt).getAllSubscriptionGroup("127.0.0.1:10911", 5000L);
    }
}
