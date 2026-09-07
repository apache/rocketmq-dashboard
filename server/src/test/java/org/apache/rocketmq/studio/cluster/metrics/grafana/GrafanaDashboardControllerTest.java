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
package org.apache.rocketmq.studio.cluster.metrics.grafana;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GrafanaDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class GrafanaDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GrafanaDashboardService grafanaDashboardService;

    @Test
    void listDashboardsShouldReturnMetadatas() throws Exception {
        when(grafanaDashboardService.listDashboards()).thenReturn(List.of(
                new GrafanaDashboardInfo("rocketmq-overview", "RocketMQ Cluster Overview",
                        "Overview", List.of("rocketmq")),
                new GrafanaDashboardInfo("rocketmq-broker", "RocketMQ Broker",
                        "Broker", List.of("rocketmq"))
        ));

        mockMvc.perform(get("/api/metrics/grafana/dashboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].uid").value("rocketmq-overview"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("rocketmq"));
    }

    @Test
    void getDashboardShouldReturnModel() throws Exception {
        when(grafanaDashboardService.getDashboard("rocketmq-overview"))
                .thenReturn(Map.of("uid", "rocketmq-overview", "title", "RocketMQ Cluster Overview"));

        mockMvc.perform(get("/api/metrics/grafana/dashboards/rocketmq-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.uid").value("rocketmq-overview"))
                .andExpect(jsonPath("$.data.title").value("RocketMQ Cluster Overview"));
    }

    @Test
    void exportDashboardShouldReturnAttachment() throws Exception {
        when(grafanaDashboardService.getDashboardJson("rocketmq-overview"))
                .thenReturn("{\"uid\":\"rocketmq-overview\"}");

        mockMvc.perform(get("/api/metrics/grafana/dashboards/rocketmq-overview/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"rocketmq-overview.json\""))
                .andExpect(content().json("{\"uid\":\"rocketmq-overview\"}"));
    }

    @Test
    void exportDashboardsShouldReturnZipAttachment() throws Exception {
        byte[] archive = "zip-content".getBytes();
        when(grafanaDashboardService.getDashboardsArchive()).thenReturn(archive);

        mockMvc.perform(get("/api/metrics/grafana/dashboards/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"rocketmq-grafana-dashboards.zip\""))
                .andExpect(content().bytes(archive));
    }
}
