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

import org.apache.rocketmq.dashboard.model.ClusterInspectionReport;
import org.apache.rocketmq.dashboard.service.ClusterInspectionService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClusterInspectionControllerTest extends BaseControllerTest {

    @InjectMocks
    private ClusterInspectionController clusterInspectionController;

    @Mock
    private ClusterInspectionService clusterInspectionService;

    @Test
    public void testInspectClusterTopology() throws Exception {
        final String url = "/cluster-inspection/inspect.query";

        ClusterInspectionReport report = new ClusterInspectionReport();
        report.setInspectTime(System.currentTimeMillis());
        report.setClusterCount(1);
        report.setTotalBrokerMasterCount(2);
        report.setTotalBrokerSlaveCount(2);
        report.setHealthScore(90);
        report.setOverallStatus("HEALTHY");

        ClusterInspectionReport.ClusterSummary summary = new ClusterInspectionReport.ClusterSummary();
        summary.setClusterName("DefaultCluster");
        summary.setMasterCount(2);
        summary.setSlaveCount(2);
        summary.setStatus("HEALTHY");
        List<ClusterInspectionReport.ClusterSummary> summaryList = new ArrayList<>();
        summaryList.add(summary);
        report.setClusters(summaryList);

        when(clusterInspectionService.inspectClusterTopology()).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterCount").value(1))
                .andExpect(jsonPath("$.totalBrokerMasterCount").value(2))
                .andExpect(jsonPath("$.totalBrokerSlaveCount").value(2))
                .andExpect(jsonPath("$.healthScore").value(90))
                .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.clusters[0].clusterName").value("DefaultCluster"));
    }
}
