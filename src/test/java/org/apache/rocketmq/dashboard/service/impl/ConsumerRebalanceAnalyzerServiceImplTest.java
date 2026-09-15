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

import org.apache.rocketmq.dashboard.model.ConsumerRebalanceHistoryReport;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConsumerRebalanceAnalyzerServiceImplTest {

    @Mock
    private ConsumerService consumerService;

    @InjectMocks
    private ConsumerRebalanceAnalyzerServiceImpl consumerRebalanceAnalyzerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAnalyzeRebalanceHistory() {
        ConsumerRebalanceHistoryReport report =
            consumerRebalanceAnalyzerService.analyzeRebalanceHistory("test-group", 24);

        Assert.assertNotNull(report);
        Assert.assertEquals("test-group", report.getConsumerGroup());
        Assert.assertTrue(report.getTotalRebalanceEvents() > 0);
        Assert.assertNotNull(report.getRebalanceEvents());
        Assert.assertFalse(report.getRebalanceEvents().isEmpty());
        Assert.assertNotNull(report.getFlappingQueues());
        Assert.assertFalse(report.getFlappingQueues().isEmpty());
        Assert.assertNotNull(report.getStabilityLevel());
        Assert.assertNotNull(report.getStabilityRecommendations());
    }

    @Test
    public void testAnalyzeRebalanceHistoryBlankGroup() {
        ConsumerRebalanceHistoryReport report =
            consumerRebalanceAnalyzerService.analyzeRebalanceHistory("", 12);

        Assert.assertNotNull(report);
        Assert.assertEquals("DEFAULT_GROUP", report.getConsumerGroup());
    }
}
