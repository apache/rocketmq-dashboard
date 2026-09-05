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

import org.apache.rocketmq.dashboard.model.ConsumerSlowRootCauseReport;
import org.apache.rocketmq.dashboard.service.ConsumerSlowRootCauseService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsumerSlowRootCauseControllerTest extends BaseControllerTest {

    @InjectMocks
    private ConsumerSlowRootCauseController consumerSlowRootCauseController;

    @Mock
    private ConsumerSlowRootCauseService consumerSlowRootCauseService;

    @Test
    public void testDiagnoseSlowRootCause() throws Exception {
        final String url = "/consumer/slowRootCause.query";

        ConsumerSlowRootCauseReport report = new ConsumerSlowRootCauseReport();
        report.setConsumerGroup("benchmark_group");
        report.setDiagnoseTime(System.currentTimeMillis());
        report.setTotalClients(2);
        report.setPrimaryRootCause("THREAD_BLOCKED");
        report.setSeverity("CRITICAL");
        report.setRootCauseDescription("Detected consumer clients with threads stuck in BLOCKED state.");
        report.setActionableRemedy("Inspect thread dumps for downstream lock contention.");

        ConsumerSlowRootCauseReport.ClientDiagnosticFinding finding =
                new ConsumerSlowRootCauseReport.ClientDiagnosticFinding();
        finding.setClientId("client-1");
        finding.setClientAddr("192.168.1.5:4000");
        finding.setBlockedThreadDetected(true);
        finding.setBlockedThreadSignature("Thread blocked in socket read");

        List<ConsumerSlowRootCauseReport.ClientDiagnosticFinding> list = new ArrayList<>();
        list.add(finding);
        report.setFindings(list);

        when(consumerSlowRootCauseService.analyzeSlowRootCause(anyString())).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url).param("consumerGroup", "benchmark_group");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerGroup").value("benchmark_group"))
                .andExpect(jsonPath("$.totalClients").value(2))
                .andExpect(jsonPath("$.primaryRootCause").value("THREAD_BLOCKED"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.findings[0].blockedThreadDetected").value(true));
    }
}
