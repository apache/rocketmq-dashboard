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

import org.apache.rocketmq.dashboard.model.BrokerConfigDriftReport;
import org.apache.rocketmq.dashboard.service.BrokerConfigDriftService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class BrokerConfigDriftControllerTest extends BaseControllerTest {

    @InjectMocks
    private BrokerConfigDriftController brokerConfigDriftController;

    @Mock
    private BrokerConfigDriftService brokerConfigDriftService;

    @Test
    public void testInspectConfigDrift() throws Exception {
        final String url = "/broker-config-drift/inspect.query";

        BrokerConfigDriftReport mockReport = new BrokerConfigDriftReport();
        mockReport.setInspectTime(System.currentTimeMillis());
        mockReport.setTotalClusters(1);
        mockReport.setTotalBrokers(2);
        mockReport.setTotalDriftItems(1);
        mockReport.setHighSeverityCount(1);
        mockReport.setMediumSeverityCount(0);
        mockReport.setLowSeverityCount(0);

        BrokerConfigDriftReport.DriftItem item = new BrokerConfigDriftReport.DriftItem();
        item.setClusterName("DefaultCluster");
        item.setPropertyKey("brokerRole");
        item.setSeverity("HIGH");
        Map<String, String> values = new HashMap<>();
        values.put("127.0.0.1:10911", "ASYNC_MASTER");
        values.put("127.0.0.1:10912", "SYNC_MASTER");
        item.setBrokerValues(values);
        item.setRecommendedAction("Align brokerRole configuration across all cluster nodes");

        List<BrokerConfigDriftReport.DriftItem> driftList = new ArrayList<>();
        driftList.add(item);
        mockReport.setDriftItems(driftList);

        when(brokerConfigDriftService.inspectConfigDrift()).thenReturn(mockReport);

        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClusters").value(1))
                .andExpect(jsonPath("$.totalBrokers").value(2))
                .andExpect(jsonPath("$.totalDriftItems").value(1))
                .andExpect(jsonPath("$.highSeverityCount").value(1))
                .andExpect(jsonPath("$.driftItems[0].propertyKey").value("brokerRole"))
                .andExpect(jsonPath("$.driftItems[0].severity").value("HIGH"));
    }

    @Test
    public void testExportSnapshot() throws Exception {
        final String url = "/broker-config-drift/snapshot.query";

        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("brokerName", "broker-a");
        snapshot.put("brokerClusterName", "DefaultCluster");
        snapshot.put("brokerRole", "ASYNC_MASTER");

        when(brokerConfigDriftService.exportBrokerConfigSnapshot(anyString())).thenReturn(snapshot);

        requestBuilder = MockMvcRequestBuilders.get(url).param("brokerAddr", "127.0.0.1:10911");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerName").value("broker-a"))
                .andExpect(jsonPath("$.brokerRole").value("ASYNC_MASTER"));
    }
}
