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

import org.apache.rocketmq.dashboard.model.MessageSchemaReport;
import org.apache.rocketmq.dashboard.service.MessageSchemaRegistryService;
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

public class MessageSchemaRegistryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageSchemaRegistryService messageSchemaRegistryService;

    @InjectMocks
    private MessageSchemaRegistryController messageSchemaRegistryController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(messageSchemaRegistryController).build();
    }

    @Test
    public void testGetSchemaOverview() throws Exception {
        MessageSchemaReport report = new MessageSchemaReport();
        report.setTopic("order-topic");
        report.setCurrentVersion(2);
        report.setCompatibilityMode("BACKWARD");

        when(messageSchemaRegistryService.getSchemaReport("order-topic")).thenReturn(report);

        mockMvc.perform(get("/schema/overview.query")
            .param("topic", "order-topic")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("order-topic"))
            .andExpect(jsonPath("$.currentVersion").value(2))
            .andExpect(jsonPath("$.compatibilityMode").value("BACKWARD"));
    }

    @Test
    public void testTestCompatibility() throws Exception {
        MessageSchemaReport report = new MessageSchemaReport();
        report.setTopic("order-topic");
        report.setEvolutionCompatible(true);

        when(messageSchemaRegistryService.testSchemaEvolution(anyString(), anyString(), anyString())).thenReturn(report);

        mockMvc.perform(post("/schema/compatibility/test.do")
            .param("topic", "order-topic")
            .param("compatibilityMode", "BACKWARD")
            .contentType(MediaType.TEXT_PLAIN)
            .content("{\"type\": \"object\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("order-topic"))
            .andExpect(jsonPath("$.evolutionCompatible").value(true));
    }

    @Test
    public void testValidatePayload() throws Exception {
        when(messageSchemaRegistryService.validatePayload(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/schema/payload/validate.do")
            .param("topic", "order-topic")
            .contentType(MediaType.TEXT_PLAIN)
            .content("{\"orderId\": \"1001\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.topic").value("order-topic"));
    }
}
