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

package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.model.MessageTraceWaterfallReport;
import org.apache.rocketmq.dashboard.service.MessageTraceWaterfallService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MessageTraceWaterfallControllerTest extends BaseControllerTest {

    @InjectMocks
    private MessageTraceWaterfallController messageTraceWaterfallController;

    @Mock
    private MessageTraceWaterfallService messageTraceWaterfallService;

    @Test
    public void testQueryWaterfall() throws Exception {
        final String url = "/messageTrace/waterfall.query";

        MessageTraceWaterfallReport report = new MessageTraceWaterfallReport();
        report.setMsgId("0A00000100002A9F0000000000000001");
        report.setTopic("TopicTest");
        report.setTotalE2eLatencyMs(350L);
        report.setTimeout(false);
        report.setBottleneckPhase("CONSUMER_EXECUTION");

        MessageTraceWaterfallReport.TraceSpanNode span = new MessageTraceWaterfallReport.TraceSpanNode();
        span.setSpanId("span_pub_1");
        span.setStage("PRODUCER_SEND");
        span.setDurationMs(25L);
        span.setStatus("SUCCESS");

        List<MessageTraceWaterfallReport.TraceSpanNode> spanList = new ArrayList<>();
        spanList.add(span);
        report.setSpanNodes(spanList);

        when(messageTraceWaterfallService.analyzeMessageTraceWaterfall(anyString(), isNull())).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url).param("msgId", "0A00000100002A9F0000000000000001");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.msgId").value("0A00000100002A9F0000000000000001"))
                .andExpect(jsonPath("$.topic").value("TopicTest"))
                .andExpect(jsonPath("$.totalE2eLatencyMs").value(350))
                .andExpect(jsonPath("$.bottleneckPhase").value("CONSUMER_EXECUTION"))
                .andExpect(jsonPath("$.spanNodes[0].stage").value("PRODUCER_SEND"));
    }
}
