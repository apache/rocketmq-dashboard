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

import org.apache.rocketmq.common.admin.TopicOffset;
import org.apache.rocketmq.common.admin.TopicStatsTable;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.model.TopicTrafficSkewReport;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TopicTrafficSkewServiceImplTest {

    @InjectMocks
    private TopicTrafficSkewServiceImpl topicTrafficSkewService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Test
    public void testInspectTopicTrafficSkewBalanced() throws Exception {
        String topic = "TopicBalanced";
        TopicStatsTable table = new TopicStatsTable();
        HashMap<MessageQueue, TopicOffset> offsetTable = new HashMap<>();

        for (int i = 0; i < 4; i++) {
            MessageQueue mq = new MessageQueue(topic, "broker-a", i);
            TopicOffset offset = new TopicOffset();
            offset.setMinOffset(0L);
            offset.setMaxOffset(1000L);
            offsetTable.put(mq, offset);
        }
        table.setOffsetTable(offsetTable);

        when(mqAdminExt.examineTopicStats(anyString())).thenReturn(table);

        TopicTrafficSkewReport report = topicTrafficSkewService.inspectTopicTrafficSkew(topic);
        Assert.assertNotNull(report);
        Assert.assertEquals(4, report.getTotalQueues());
        Assert.assertEquals(4000L, report.getTotalMessages());
        Assert.assertEquals("BALANCED", report.getSkewLevel());
        Assert.assertEquals(0.0, report.getGiniCoefficient(), 0.01);
    }

    @Test
    public void testInspectTopicTrafficSkewSevere() throws Exception {
        String topic = "TopicSkewed";
        TopicStatsTable table = new TopicStatsTable();
        HashMap<MessageQueue, TopicOffset> offsetTable = new HashMap<>();

        MessageQueue mq0 = new MessageQueue(topic, "broker-a", 0);
        TopicOffset offset0 = new TopicOffset();
        offset0.setMinOffset(0L);
        offset0.setMaxOffset(90000L);
        offsetTable.put(mq0, offset0);

        for (int i = 1; i < 4; i++) {
            MessageQueue mq = new MessageQueue(topic, "broker-a", i);
            TopicOffset offset = new TopicOffset();
            offset.setMinOffset(0L);
            offset.setMaxOffset(100L);
            offsetTable.put(mq, offset);
        }
        table.setOffsetTable(offsetTable);

        when(mqAdminExt.examineTopicStats(anyString())).thenReturn(table);

        TopicTrafficSkewReport report = topicTrafficSkewService.inspectTopicTrafficSkew(topic);
        Assert.assertNotNull(report);
        Assert.assertEquals(4, report.getTotalQueues());
        Assert.assertEquals("SEVERE_SKEW", report.getSkewLevel());
        Assert.assertTrue(report.getGiniCoefficient() > 0.5);
        Assert.assertTrue(report.getQueueDetails().stream().anyMatch(TopicTrafficSkewReport.QueueSkewDetail::isHotspot));
    }
}
