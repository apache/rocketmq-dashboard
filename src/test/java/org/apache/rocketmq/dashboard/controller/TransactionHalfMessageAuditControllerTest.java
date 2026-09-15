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

import org.apache.rocketmq.dashboard.model.TransactionHalfMessageAuditReport;
import org.apache.rocketmq.dashboard.service.TransactionHalfMessageAuditService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TransactionHalfMessageAuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TransactionHalfMessageAuditService transactionHalfMessageAuditService;

    @InjectMocks
    private TransactionHalfMessageAuditController transactionHalfMessageAuditController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(transactionHalfMessageAuditController).build();
    }

    @Test
    public void testQueryHalfAudit() throws Exception {
        TransactionHalfMessageAuditReport report = new TransactionHalfMessageAuditReport();
        report.setTopic("tx-topic");
        report.setProducerGroup("pg-tx");
        report.setTotalPendingHalfMessages(10);
        report.setHealthStatus("HEALTHY");

        when(transactionHalfMessageAuditService.auditPendingHalfMessages(anyString(), anyString()))
            .thenReturn(report);

        mockMvc.perform(get("/transaction/halfAudit.query")
            .param("topic", "tx-topic")
            .param("producerGroup", "pg-tx")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("tx-topic"))
            .andExpect(jsonPath("$.producerGroup").value("pg-tx"))
            .andExpect(jsonPath("$.totalPendingHalfMessages").value(10))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"));
    }

    @Test
    public void testResolveTransaction() throws Exception {
        when(transactionHalfMessageAuditService.resolveTransaction(anyString(), anyString(), anyString()))
            .thenReturn(true);

        mockMvc.perform(post("/transaction/halfResolve.do")
            .param("msgId", "msg-1")
            .param("transactionId", "tx-1")
            .param("action", "COMMIT")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.action").value("COMMIT"));
    }
}
