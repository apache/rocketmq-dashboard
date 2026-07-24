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
package com.rocketmq.studio.cluster.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private MetricProfileService metricProfileService;

    @Test
    void listProfilesShouldReturnVersionAwareMappings() throws Exception {
        MetricProfileVO profile = MetricProfileVO.builder()
                .id("rocketmq5-native")
                .name("RocketMQ 5.x Native")
                .metrics(List.of(MetricProfileVO.MetricMappingVO.builder()
                        .semanticMetric("message_in_tps")
                        .prometheusMetric("rocketmq_messages_in_total")
                        .promql("sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)")
                        .labels(List.of("cluster", "node_id"))
                        .build()))
                .build();
        when(metricProfileService.listProfiles()).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/metrics/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("rocketmq5-native"))
                .andExpect(jsonPath("$.data[0].metrics[0].semanticMetric").value("message_in_tps"))
                .andExpect(jsonPath("$.data[0].metrics[0].prometheusMetric")
                        .value("rocketmq_messages_in_total"));
    }

    @Test
    void queryShouldReturnBadRequestWhenFieldTypeIsInvalid() throws Exception {
        mockMvc.perform(post("/api/metrics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metric":"up","start":"abc","end":123,"step":"30s"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(metricsService);
    }
}
