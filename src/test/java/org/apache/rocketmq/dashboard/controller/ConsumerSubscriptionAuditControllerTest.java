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

import org.apache.rocketmq.dashboard.model.ConsumerSubscriptionAuditReport;
import org.apache.rocketmq.dashboard.service.ConsumerSubscriptionAuditService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsumerSubscriptionAuditControllerTest extends BaseControllerTest {

    @InjectMocks
    private ConsumerSubscriptionAuditController consumerSubscriptionAuditController;

    @Mock
    private ConsumerSubscriptionAuditService consumerSubscriptionAuditService;

    @Test
    public void testAuditSubscription() throws Exception {
        final String url = "/consumer/subscriptionAudit.query";

        ConsumerSubscriptionAuditReport report = new ConsumerSubscriptionAuditReport();
        report.setConsumerGroup("benchmark_group");
        report.setAuditTime(System.currentTimeMillis());
        report.setTotalClients(2);
        report.setConsistent(false);
        report.setConflictItemCount(1);
        report.setAuditStatus("INCONSISTENT_SUBSCRIPTIONS");
        report.setRecommendation("Discrepancies found across group instances.");

        ConsumerSubscriptionAuditReport.SubscriptionConflictItem item =
                new ConsumerSubscriptionAuditReport.SubscriptionConflictItem();
        item.setTopic("TopicTest");
        item.setConflictType("SUB_EXPRESSION_MISMATCH");
        item.setDescription("Conflict in filter expressions");
        Map<String, String> expressions = new HashMap<>();
        expressions.put("client-1", "TagA");
        expressions.put("client-2", "TagB");
        item.setClientExpressions(expressions);

        List<ConsumerSubscriptionAuditReport.SubscriptionConflictItem> list = new ArrayList<>();
        list.add(item);
        report.setConflictItems(list);

        when(consumerSubscriptionAuditService.auditSubscriptionConsistency(anyString())).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url).param("consumerGroup", "benchmark_group");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerGroup").value("benchmark_group"))
                .andExpect(jsonPath("$.totalClients").value(2))
                .andExpect(jsonPath("$.consistent").value(false))
                .andExpect(jsonPath("$.conflictItemCount").value(1))
                .andExpect(jsonPath("$.conflictItems[0].conflictType").value("SUB_EXPRESSION_MISMATCH"));
    }
}
