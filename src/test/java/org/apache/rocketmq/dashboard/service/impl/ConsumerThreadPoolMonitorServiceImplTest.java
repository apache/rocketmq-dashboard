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
import org.apache.rocketmq.dashboard.model.ConsumerThreadPoolSaturationReport;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ConsumerThreadPoolMonitorServiceImplTest {

    @Mock
    private ConsumerService consumerService;

    @InjectMocks
    private ConsumerThreadPoolMonitorServiceImpl consumerThreadPoolMonitorService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInspectThreadPoolSaturationWithConnection() {
        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> set = new HashSet<>();
        Connection c1 = new Connection();
        c1.setClientId("client-a");
        c1.setClientAddr("192.168.1.100:61234");
        set.add(c1);
        connection.setConnectionSet(set);

        when(consumerService.getConsumerConnection(anyString())).thenReturn(connection);

        ConsumerThreadPoolSaturationReport report =
            consumerThreadPoolMonitorService.inspectThreadPoolSaturation("test-group", null);

        Assert.assertNotNull(report);
        Assert.assertEquals("test-group", report.getConsumerGroup());
        Assert.assertEquals(1, report.getTotalClients());
        Assert.assertNotNull(report.getClientThreadPoolStats());
        Assert.assertEquals(1, report.getClientThreadPoolStats().size());
        Assert.assertNotNull(report.getHealthStatus());
    }

    @Test
    public void testInspectThreadPoolSaturationFallback() {
        when(consumerService.getConsumerConnection(anyString())).thenThrow(new RuntimeException("Offline"));

        ConsumerThreadPoolSaturationReport report =
            consumerThreadPoolMonitorService.inspectThreadPoolSaturation("test-group", null);

        Assert.assertNotNull(report);
        Assert.assertEquals(2, report.getTotalClients());
        Assert.assertNotNull(report.getOptimizationRecommendations());
    }
}
