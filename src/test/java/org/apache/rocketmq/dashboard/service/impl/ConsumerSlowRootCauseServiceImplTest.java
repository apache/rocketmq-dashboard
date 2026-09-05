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

import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.body.Connection;
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.common.protocol.body.ProcessQueueInfo;
import org.apache.rocketmq.dashboard.model.ConsumerSlowRootCauseReport;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.TreeMap;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerSlowRootCauseServiceImplTest {

    @InjectMocks
    private ConsumerSlowRootCauseServiceImpl consumerSlowRootCauseService;

    @Mock
    private ConsumerService consumerService;

    @Test
    public void testAnalyzeSlowRootCauseFlowControl() {
        String group = "TestSlowGroup";
        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> clients = new HashSet<>();
        Connection conn = new Connection();
        conn.setClientId("client_flow_ctrl");
        conn.setClientAddr("192.168.1.10:3000");
        clients.add(conn);
        connection.setConnectionSet(clients);

        when(consumerService.getConsumerConnection(group)).thenReturn(connection);

        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        TreeMap<MessageQueue, ProcessQueueInfo> mqTable = new TreeMap<>();
        MessageQueue mq = new MessageQueue("TopicTest", "broker-a", 0);
        ProcessQueueInfo pq = new ProcessQueueInfo();
        pq.setCachedMsgCount(2500);
        mqTable.put(mq, pq);
        runningInfo.setMqTable(mqTable);

        when(consumerService.getConsumerRunningInfo(anyString(), anyString(), anyBoolean())).thenReturn(runningInfo);

        ConsumerSlowRootCauseReport report = consumerSlowRootCauseService.analyzeSlowRootCause(group);
        Assert.assertNotNull(report);
        Assert.assertEquals(1, report.getTotalClients());
        Assert.assertEquals("FLOW_CONTROL_EXCEEDED", report.getPrimaryRootCause());
        Assert.assertEquals("WARNING", report.getSeverity());
    }

    @Test
    public void testAnalyzeSlowRootCauseBlockedThread() {
        String group = "TestBlockedGroup";
        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> clients = new HashSet<>();
        Connection conn = new Connection();
        conn.setClientId("client_blocked");
        conn.setClientAddr("192.168.1.11:3000");
        clients.add(conn);
        connection.setConnectionSet(clients);

        when(consumerService.getConsumerConnection(group)).thenReturn(connection);

        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("\"ConsumeMessageThread_1\" prio=5 tid=0x0000 nid=0x1 waiting to lock <0x001>");

        when(consumerService.getConsumerRunningInfo(anyString(), anyString(), anyBoolean())).thenReturn(runningInfo);

        ConsumerSlowRootCauseReport report = consumerSlowRootCauseService.analyzeSlowRootCause(group);
        Assert.assertNotNull(report);
        Assert.assertEquals(1, report.getTotalClients());
        Assert.assertEquals("THREAD_BLOCKED", report.getPrimaryRootCause());
        Assert.assertEquals("CRITICAL", report.getSeverity());
        Assert.assertTrue(report.getFindings().get(0).isBlockedThreadDetected());
    }
}
