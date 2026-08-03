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
package org.apache.rocketmq.studio.cluster.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricsDataSourceController.class)
@AutoConfigureMockMvc(addFilters = false)
class MetricsDataSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsDataSourceService dataSourceService;

    @Test
    void listDataSourcesShouldReturnConfiguredSources() throws Exception {
        when(dataSourceService.listDataSources()).thenReturn(List.of());

        mockMvc.perform(get("/api/metrics/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void queryShouldReturnMetricDataForNamedDataSource() throws Exception {
        when(dataSourceService.query(eq("victoriametrics-prod"), any(MetricQueryDTO.class)))
                .thenReturn(MetricDataVO.builder().resultType("matrix").series(List.of()).warnings(List.of()).build());

        mockMvc.perform(post("/api/metrics/datasources/query?dataSource=victoriametrics-prod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metric":"up","start":1784107658,"end":1784108558,"step":"30s"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createDataSourceShouldReturnCreatedConfig() throws Exception {
        when(dataSourceService.createDataSource(any())).thenReturn(new org.apache.rocketmq.studio.model.MetricsDataSourceConfig());

        mockMvc.perform(post("/api/metrics/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"cortex-prod","providerType":"CORTEX","url":"http://cortex:9009"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteDataSourceShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/metrics/datasources?name=prometheus-prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(dataSourceService).deleteDataSource("prometheus-prod");
    }
}
