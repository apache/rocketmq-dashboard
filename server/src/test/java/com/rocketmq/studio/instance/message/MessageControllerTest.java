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

package com.rocketmq.studio.instance.message;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @Test
    void queryMessagesShouldPassTagFilter() throws Exception {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-001")
                .topic("orders")
                .tag("created")
                .key("order-1")
                .build();
        when(messageService.queryMessages(eq("orders"), isNull(), eq("created"), eq("order-1"),
                eq(1784246400000L), eq(1784332800000L))).thenReturn(List.of(message));

        mockMvc.perform(get("/api/messages")
                        .param("topic", "orders")
                        .param("tag", "created")
                        .param("key", "order-1")
                        .param("startTime", "1784246400000")
                        .param("endTime", "1784332800000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].msgId").value("msg-001"))
                .andExpect(jsonPath("$.data[0].tag").value("created"));

        verify(messageService).queryMessages(eq("orders"), isNull(), eq("created"), eq("order-1"),
                eq(1784246400000L), eq(1784332800000L));
    }
}
