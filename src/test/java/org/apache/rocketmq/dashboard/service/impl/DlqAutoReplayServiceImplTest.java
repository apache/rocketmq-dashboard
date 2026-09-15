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

import org.apache.rocketmq.common.protocol.body.GroupList;
import org.apache.rocketmq.dashboard.model.DlqAutoReplayReport;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class DlqAutoReplayServiceImplTest {

    @Mock
    private MQAdminExt mqAdminExt;

    @InjectMocks
    private DlqAutoReplayServiceImpl dlqAutoReplayService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetReplayStatus() throws Exception {
        GroupList groupList = new GroupList();
        groupList.setGroupList(new HashSet<>());
        when(mqAdminExt.queryTopicConsumeByWho(anyString())).thenReturn(groupList);

        DlqAutoReplayReport report = dlqAutoReplayService.getReplayStatus("test-group");
        Assert.assertNotNull(report);
        Assert.assertEquals("test-group", report.getConsumerGroup());
        Assert.assertTrue(report.getDlqTopic().contains("test-group"));
        Assert.assertNotNull(report.getStatus());
        Assert.assertNotNull(report.getAuditLogs());
    }

    @Test
    public void testExecuteAutoReplay() {
        DlqAutoReplayReport.ReplayPolicy policy = new DlqAutoReplayReport.ReplayPolicy();
        policy.setMaxBatchSize(100);
        policy.setRateLimitPerSecond(20);

        DlqAutoReplayReport report = dlqAutoReplayService.executeAutoReplay("test-group", policy);
        Assert.assertNotNull(report);
        Assert.assertTrue(report.getReplayedMessages() > 0);
        Assert.assertNotNull(report.getExecutionHistory());
        Assert.assertFalse(report.getExecutionHistory().isEmpty());
        Assert.assertEquals(100, report.getExecutionHistory().get(0).getCount());
    }
}
