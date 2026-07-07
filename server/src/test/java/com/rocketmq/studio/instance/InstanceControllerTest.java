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

package com.rocketmq.studio.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.common.domain.enums.InstanceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InstanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstanceService instanceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listInstancesShouldReturnAllInstances() throws Exception {
        InstanceVO inst = buildInstance("inst-1", "production-proxy", InstanceType.PROXY, "10.0.1.1:8080");

        when(instanceService.listInstances(isNull(), isNull())).thenReturn(List.of(inst));

        mockMvc.perform(get("/api/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("inst-1"))
                .andExpect(jsonPath("$.data[0].name").value("production-proxy"))
                .andExpect(jsonPath("$.data[0].type").value("PROXY"))
                .andExpect(jsonPath("$.data[0].endpoint").value("10.0.1.1:8080"));
    }

    @Test
    void listInstancesShouldFilterByType() throws Exception {
        InstanceVO inst = buildInstance("inst-1", "proxy-1", InstanceType.PROXY, "10.0.1.1:8080");

        when(instanceService.listInstances(eq(InstanceType.PROXY), isNull())).thenReturn(List.of(inst));

        mockMvc.perform(get("/api/instances")
                        .param("type", "PROXY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].type").value("PROXY"));

        verify(instanceService).listInstances(eq(InstanceType.PROXY), isNull());
    }

    @Test
    void listInstancesShouldFilterBySearch() throws Exception {
        InstanceVO inst = buildInstance("inst-1", "production", InstanceType.PROXY, "10.0.1.1:8080");

        when(instanceService.listInstances(isNull(), eq("prod"))).thenReturn(List.of(inst));

        mockMvc.perform(get("/api/instances")
                        .param("search", "prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("production"));

        verify(instanceService).listInstances(isNull(), eq("prod"));
    }

    @Test
    void createInstanceShouldReturnCreatedInstance() throws Exception {
        InstanceVO input = InstanceVO.builder()
                .name("new-instance")
                .endpoint("10.0.2.1:8080")
                .type(InstanceType.DIRECT)
                .build();

        InstanceVO created = InstanceVO.builder()
                .name("new-instance")
                .endpoint("10.0.2.1:8080")
                .type(InstanceType.DIRECT)
                .topicCount(0)
                .consumerGroupCount(0)
                .build();
        created.setId("new-id");
        created.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        created.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(instanceService.createInstance(any(InstanceVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/instances/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("new-id"))
                .andExpect(jsonPath("$.data.name").value("new-instance"))
                .andExpect(jsonPath("$.data.endpoint").value("10.0.2.1:8080"))
                .andExpect(jsonPath("$.data.type").value("DIRECT"));
    }

    @Test
    void updateInstanceShouldReturnUpdatedInstance() throws Exception {
        InstanceVO update = InstanceVO.builder()
                .name("updated-name")
                .build();
        update.setId("inst-1");

        InstanceVO updated = InstanceVO.builder()
                .name("updated-name")
                .endpoint("10.0.1.1:8080")
                .type(InstanceType.PROXY)
                .build();
        updated.setId("inst-1");
        updated.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        updated.setUpdatedAt(LocalDateTime.of(2026, 7, 8, 12, 0));

        when(instanceService.updateInstance(any(InstanceVO.class))).thenReturn(updated);

        mockMvc.perform(post("/api/instances/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("inst-1"))
                .andExpect(jsonPath("$.data.name").value("updated-name"));
    }

    @Test
    void deleteInstanceShouldReturnSuccess() throws Exception {
        doNothing().when(instanceService).deleteInstance("inst-1");

        Map<String, String> body = Map.of("id", "inst-1");

        mockMvc.perform(post("/api/instances/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(instanceService).deleteInstance("inst-1");
    }

    private InstanceVO buildInstance(String id, String name, InstanceType type, String endpoint) {
        InstanceVO instance = InstanceVO.builder()
                .name(name)
                .type(type)
                .endpoint(endpoint)
                .topicCount(10)
                .consumerGroupCount(5)
                .build();
        instance.setId(id);
        instance.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        instance.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return instance;
    }
}
