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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.dashboard.model.MessageBatchOperationRequest;
import org.apache.rocketmq.dashboard.model.MessageBatchOperationResult;
import org.apache.rocketmq.dashboard.service.MessageBatchOperationService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MessageBatchOperationControllerTest extends BaseControllerTest {

    @InjectMocks
    private MessageBatchOperationController messageBatchOperationController;

    @Mock
    private MessageBatchOperationService messageBatchOperationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testBatchResend() throws Exception {
        final String url = "/message-batch/resend.do";

        MessageBatchOperationRequest request = new MessageBatchOperationRequest();
        request.setTopic("TopicTest");
        request.setMsgIds(Arrays.asList("0A00000100002A9F0000000000000001", "0A00000100002A9F0000000000000002"));
        request.setTargetTopic("TopicTest");
        request.setRateLimitQps(50);

        MessageBatchOperationResult result = new MessageBatchOperationResult();
        result.setTotalRequested(2);
        result.setSuccessCount(2);
        result.setFailedCount(0);
        result.setSkippedCount(0);
        result.setElapsedTimeMs(120);

        when(messageBatchOperationService.batchResendMessages(any(MessageBatchOperationRequest.class))).thenReturn(result);

        requestBuilder = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));

        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    public void testExportCsv() throws Exception {
        final String url = "/message-batch/export.do";

        MessageBatchOperationRequest request = new MessageBatchOperationRequest();
        request.setTopic("TopicTest");
        request.setMsgIds(Arrays.asList("0A00000100002A9F0000000000000001"));

        String mockCsv = "MessageId,Topic,Tag,Keys,BornTimestamp,StoreTimestamp,BornHost,StoreHost,Size,Body\n"
                + "0A00000100002A9F0000000000000001,TopicTest,TagA,Key1,1700000000000,1700000000100,127.0.0.1,127.0.0.1,128,test body\n";

        when(messageBatchOperationService.exportMessagesAsCsv(any(MessageBatchOperationRequest.class))).thenReturn(mockCsv);

        requestBuilder = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));

        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("text/csv")))
                .andExpect(content().string(mockCsv));
    }
}
