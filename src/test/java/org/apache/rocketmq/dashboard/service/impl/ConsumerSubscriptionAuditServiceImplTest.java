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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.common.protocol.body.Connection;
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.dashboard.model.ConsumerSubscriptionAuditReport;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerSubscriptionAuditServiceImplTest {

    @InjectMocks
    private ConsumerSubscriptionAuditServiceImpl consumerSubscriptionAuditService;

    @Mock
    private ConsumerService consumerService;

    @Test
    public void testAuditSubscriptionConsistencySuccess() {
        String group = "TestGroup";
        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> set = new HashSet<>();

        Connection conn1 = new Connection();
        conn1.setClientId("client_1");
        conn1.setClientAddr("192.168.1.1:5000");
        set.add(conn1);

        Connection conn2 = new Connection();
        conn2.setClientId("client_2");
        conn2.setClientAddr("192.168.1.2:5000");
        set.add(conn2);

        connection.setConnectionSet(set);

        ConcurrentHashMap<String, SubscriptionData> subTable = new ConcurrentHashMap<>();
        SubscriptionData data = new SubscriptionData();
        data.setTopic("TopicA");
        data.setSubString("TagA || TagB");
        subTable.put("TopicA", data);
        connection.setSubscriptionTable(subTable);

        when(consumerService.getConsumerConnection(anyString())).thenReturn(connection);

        ConsumerSubscriptionAuditReport report = consumerSubscriptionAuditService.auditSubscriptionConsistency(group);
        Assert.assertNotNull(report);
        Assert.assertEquals(2, report.getTotalClients());
        Assert.assertTrue(report.isConsistent());
        Assert.assertEquals("CONSISTENT", report.getAuditStatus());
    }

    @Test
    public void testAuditSubscriptionInconsistent() {
        String group = "InconsistentGroup";
        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> set = new HashSet<>();

        Connection conn1 = new Connection();
        conn1.setClientId("client_1");
        set.add(conn1);

        connection.setConnectionSet(set);

        ConcurrentHashMap<String, SubscriptionData> subTable = new ConcurrentHashMap<>();
        connection.setSubscriptionTable(subTable);

        when(consumerService.getConsumerConnection(anyString())).thenReturn(connection);

        ConsumerSubscriptionAuditReport report = consumerSubscriptionAuditService.auditSubscriptionConsistency(group);
        Assert.assertNotNull(report);
        Assert.assertEquals(1, report.getTotalClients());
    }
}
