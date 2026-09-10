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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NativeAlertMetricCatalogController.class)
@AutoConfigureMockMvc(addFilters = false)
class NativeAlertMetricCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NativeAlertMetricCatalogService catalogService;

    @Test
    void listShouldForwardInstanceAndDomain() throws Exception {
        when(catalogService.list("apache", AlertDomain.CLUSTER)).thenReturn(List.of(
                new NativeAlertMetricInfo("broker.availability", "Broker availability", "", false)));

        mockMvc.perform(get("/api/native-alert-metrics")
                        .param("instanceId", "apache")
                        .param("domain", "CLUSTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("broker.availability"));

        verify(catalogService).list("apache", AlertDomain.CLUSTER);
    }

    @Test
    void listShouldRejectMissingRequiredParameters() throws Exception {
        mockMvc.perform(get("/api/native-alert-metrics"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
