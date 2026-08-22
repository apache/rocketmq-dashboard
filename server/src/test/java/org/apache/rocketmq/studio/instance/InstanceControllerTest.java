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

package org.apache.rocketmq.studio.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.InstanceCapability;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @MockBean
    private InstanceCapabilityService instanceCapabilityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listInstancesShouldReturnAllInstances() throws Exception {
        InstanceVO inst = buildInstance(1L, "production-proxy", InstanceType.PROXY_CLUSTER, "10.0.1.1:8080");

        when(instanceService.listInstances(isNull(), isNull())).thenReturn(List.of(inst));

        mockMvc.perform(get("/api/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("production-proxy"))
                .andExpect(jsonPath("$.data[0].type").value("PROXY_CLUSTER"))
                .andExpect(jsonPath("$.data[0].endpoint").value("10.0.1.1:8080"));
    }

    @Test
    void listInstancesPageShouldReturnBoundedPage() throws Exception {
        InstanceVO inst = buildInstance(1L, "production-proxy", InstanceType.PROXY_CLUSTER,
                "10.0.1.1:8080");
        when(instanceService.listInstances(isNull(), isNull(), eq(2), eq(20)))
                .thenReturn(PageResult.of(List.of(inst), 21, 2, 20));

        mockMvc.perform(get("/api/instances/page")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("production-proxy"))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(instanceService).listInstances(isNull(), isNull(), eq(2), eq(20));
    }

    @Test
    void listInstancesShouldFilterByType() throws Exception {
        InstanceVO inst = buildInstance(1L, "proxy-1", InstanceType.CLOUD, "10.0.1.1:8080");

        when(instanceService.listInstances(eq(InstanceType.CLOUD), isNull())).thenReturn(List.of(inst));

        mockMvc.perform(get("/api/instances")
                        .param("type", "CLOUD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].type").value("CLOUD"));

        verify(instanceService).listInstances(eq(InstanceType.CLOUD), isNull());
    }

    @Test
    void listInstancesShouldFilterBySearch() throws Exception {
        InstanceVO inst = buildInstance(1L, "production", InstanceType.PROXY_CLUSTER, "10.0.1.1:8080");

        when(instanceService.listInstances(isNull(), eq("prod"))).thenReturn(List.of(inst));

        mockMvc.perform(get("/api/instances")
                        .param("search", "prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("production"));

        verify(instanceService).listInstances(isNull(), eq("prod"));
    }

    @Test
    void getCapabilitiesShouldResolveStringInstanceIdAndReturnContractTest() throws Exception {
        when(instanceService.resolveInstanceId("instance-1")).thenReturn(1L);
        when(instanceCapabilityService.getCapabilities(1L)).thenReturn(new InstanceCapabilitiesVO(
                "instance-1",
                InstanceVendor.APACHE,
                InstanceType.DIRECT,
                List.of(InstanceCapability.TOPIC_MANAGEMENT, InstanceCapability.DLQ_MANAGEMENT)));

        mockMvc.perform(get("/api/instances/instance-1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instanceId").value("instance-1"))
                .andExpect(jsonPath("$.data.vendor").value("APACHE"))
                .andExpect(jsonPath("$.data.accessType").value("DIRECT"))
                .andExpect(jsonPath("$.data.capabilities[0]").value("TOPIC_MANAGEMENT"))
                .andExpect(jsonPath("$.data.capabilities[1]").value("DLQ_MANAGEMENT"));

        verify(instanceService).resolveInstanceId("instance-1");
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
        created.setId(2L);
        created.setGmtCreate(LocalDateTime.of(2026, 1, 1, 0, 0));
        created.setGmtModified(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(instanceService.createInstance(any(InstanceVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/instances/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("new-instance"))
                .andExpect(jsonPath("$.data.endpoint").value("10.0.2.1:8080"))
                .andExpect(jsonPath("$.data.type").value("DIRECT"));
    }

    @Test
    void createInstanceShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/instances/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(instanceService);
    }

    @Test
    void updateInstanceShouldReturnUpdatedInstance() throws Exception {
        InstanceVO updated = InstanceVO.builder()
                .name("updated-name")
                .endpoint("10.0.1.1:8080")
                .type(InstanceType.PROXY_CLUSTER)
                .build();
        updated.setId(1L);
        updated.setGmtCreate(LocalDateTime.of(2026, 1, 1, 0, 0));
        updated.setGmtModified(LocalDateTime.of(2026, 7, 8, 12, 0));

        when(instanceService.resolveInstanceId("instance-1")).thenReturn(1L);
        when(instanceService.updateInstance(any(InstanceVO.class))).thenReturn(updated);

        mockMvc.perform(post("/api/instances/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instanceId", "instance-1",
                                "name", "updated-name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("updated-name"));
    }

    @Test
    void updateInstanceShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/instances/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(instanceService);
    }

    @Test
    void deleteInstanceShouldReturnSuccess() throws Exception {
        when(instanceService.resolveInstanceId("instance-1")).thenReturn(1L);
        doNothing().when(instanceService).deleteInstance(1L);

        Map<String, String> body = Map.of("id", "instance-1");

        mockMvc.perform(post("/api/instances/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(instanceService).deleteInstance(1L);
    }

    @Test
    void deleteInstanceShouldReturnConflictWhenManagedResourcesExist() throws Exception {
        when(instanceService.resolveInstanceId("instance-1")).thenReturn(1L);
        doThrow(new BusinessException(409,
                "Cannot delete instance with managed resources: topics=2, consumerGroups=1"))
                .when(instanceService).deleteInstance(1L);

        mockMvc.perform(post("/api/instances/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "instance-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete instance with managed resources: topics=2, consumerGroups=1"));

        verify(instanceService).deleteInstance(1L);
    }

    @Test
    void deleteInstanceShouldRejectBlankId() throws Exception {
        mockMvc.perform(post("/api/instances/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(instanceService);
    }

    @Test
    void deleteInstanceShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/instances/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(instanceService);
    }

    private InstanceVO buildInstance(Long id, String name, InstanceType type, String endpoint) {
        InstanceVO instance = InstanceVO.builder()
                .name(name)
                .type(type)
                .endpoint(endpoint)
                .topicCount(10)
                .consumerGroupCount(5)
                .build();
        instance.setId(id);
        instance.setGmtCreate(LocalDateTime.of(2026, 1, 1, 0, 0));
        instance.setGmtModified(LocalDateTime.of(2026, 1, 1, 0, 0));
        return instance;
    }
}
