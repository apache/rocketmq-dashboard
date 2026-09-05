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

import org.apache.rocketmq.client.trace.TraceType;
import org.apache.rocketmq.dashboard.model.MessageTraceWaterfallReport;
import org.apache.rocketmq.dashboard.model.trace.TraceView;
import org.apache.rocketmq.dashboard.service.MessageTraceService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessageTraceWaterfallServiceImplTest {

    @InjectMocks
    private MessageTraceWaterfallServiceImpl messageTraceWaterfallService;

    @Mock
    private MessageTraceService messageTraceService;

    @Test
    public void testAnalyzeMessageTraceWaterfallSuccess() {
        String msgId = "0A00000100002A9F0000000000000001";
        List<TraceView> traceViews = new ArrayList<>();

        TraceView pub = new TraceView();
        pub.setMsgType(TraceType.Pub.name());
        pub.setMsgId(msgId);
        pub.setTopic("TopicTest");
        pub.setClientHost("192.168.1.50");
        pub.setStoreHost("192.168.1.100:10911");
        pub.setTimeStamp(1700000000000L);
        pub.setCostTime(30);
        pub.setStatus("SUCCESS");
        traceViews.add(pub);

        TraceView subBefore = new TraceView();
        subBefore.setMsgType(TraceType.SubBefore.name());
        subBefore.setMsgId(msgId);
        subBefore.setGroupName("group_consumer_test");
        subBefore.setClientHost("192.168.1.60");
        subBefore.setTimeStamp(1700000000100L);
        traceViews.add(subBefore);

        TraceView subAfter = new TraceView();
        subAfter.setMsgType(TraceType.SubAfter.name());
        subAfter.setMsgId(msgId);
        subAfter.setGroupName("group_consumer_test");
        subAfter.setClientHost("192.168.1.60");
        subAfter.setTimeStamp(1700000000120L);
        subAfter.setCostTime(150);
        subAfter.setStatus("SUCCESS");
        traceViews.add(subAfter);

        when(messageTraceService.queryMessageTraceByMsgId(anyString(), anyString())).thenReturn(traceViews);

        MessageTraceWaterfallReport report = messageTraceWaterfallService.analyzeMessageTraceWaterfall(msgId, null);
        Assert.assertNotNull(report);
        Assert.assertEquals(msgId, report.getMsgId());
        Assert.assertEquals("TopicTest", report.getTopic());
        Assert.assertFalse(report.getSpanNodes().isEmpty());
        Assert.assertTrue(report.getTotalE2eLatencyMs() > 0);
    }

    @Test
    public void testAnalyzeMessageTraceWaterfallEmpty() {
        when(messageTraceService.queryMessageTraceByMsgId(anyString(), anyString())).thenReturn(new ArrayList<>());

        MessageTraceWaterfallReport report = messageTraceWaterfallService.analyzeMessageTraceWaterfall("msg_none", null);
        Assert.assertNotNull(report);
        Assert.assertEquals("NO_TRACE_FOUND", report.getBottleneckPhase());
        Assert.assertTrue(report.getSpanNodes().isEmpty());
    }
}
