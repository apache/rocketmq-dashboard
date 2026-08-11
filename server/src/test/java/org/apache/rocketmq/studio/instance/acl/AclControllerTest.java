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

package org.apache.rocketmq.studio.instance.acl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AclController.class)
@AutoConfigureMockMvc(addFilters = false)
class AclControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AclService aclService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listRulesShouldReturnRules() throws Exception {
        AclRuleVO rule = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("ALLOW")
                .build();
        rule.setId("rule-1");
        rule.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(aclService.listRules(isNull(), isNull())).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/acl/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("rule-1"))
                .andExpect(jsonPath("$.data[0].principal").value("user1"))
                .andExpect(jsonPath("$.data[0].decision").value("ALLOW"));
    }

    @Test
    void listRulesShouldPassQueryParams() throws Exception {
        when(aclService.listRules(eq("cluster-1"), eq("user1"))).thenReturn(List.of());

        mockMvc.perform(get("/api/acl/rules")
                        .param("clusterId", "cluster-1")
                        .param("principal", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(aclService).listRules(eq("cluster-1"), eq("user1"));
    }

    @Test
    void createRuleShouldReturnCreatedRule() throws Exception {
        AclRuleVO input = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("ALLOW")
                .build();

        AclRuleVO created = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("ALLOW")
                .build();
        created.setId("new-rule-id");
        created.setCreatedAt(LocalDateTime.of(2026, 7, 8, 12, 0));

        when(aclService.createRule(any(AclRuleVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/acl/rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("new-rule-id"))
                .andExpect(jsonPath("$.data.principal").value("user1"));
    }

    @Test
    void createRuleShouldRejectMissingPrincipal() throws Exception {
        mockMvc.perform(post("/api/acl/rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"topic-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("principal is required"));

        verifyNoInteractions(aclService);
    }

    @Test
    void updateRuleShouldReturnUpdatedRule() throws Exception {
        AclRuleVO input = AclRuleVO.builder()
                .id("rule-1")
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("DENY")
                .build();

        when(aclService.updateRule(any(AclRuleVO.class))).thenReturn(input);

        mockMvc.perform(post("/api/acl/rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("rule-1"))
                .andExpect(jsonPath("$.data.decision").value("DENY"));
    }

    @Test
    void updateRuleShouldReturnNotFoundForUnknownId() throws Exception {
        AclRuleVO input = AclRuleVO.builder()
                .id("missing-rule")
                .principal("user1")
                .resource("topic-1")
                .decision("DENY")
                .build();
        when(aclService.updateRule(any(AclRuleVO.class)))
                .thenThrow(new BusinessException(404, "ACL rule not found: missing-rule"));

        mockMvc.perform(post("/api/acl/rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("ACL rule not found: missing-rule"));
    }

    @Test
    void updateRuleShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/acl/rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal\":\"user1\",\"resource\":\"topic-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(aclService);
    }

    @Test
    void deleteRuleShouldPassValidatedRequest() throws Exception {
        mockMvc.perform(post("/api/acl/rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "rule-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(aclService).deleteRule("rule-1");
    }

    @Test
    void deleteRuleShouldRejectBlankId() throws Exception {
        mockMvc.perform(post("/api/acl/rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(aclService);
    }

    @Test
    void listUsersShouldReturnAllUsers() throws Exception {
        AclUserVO user = AclUserVO.builder()
                .username("admin")
                .accessKey("acce****3456")
                .secretKey("secr****7654")
                .admin(true)
                .build();
        user.setId("user-1");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(aclService.listUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/acl/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("user-1"))
                .andExpect(jsonPath("$.data[0].username").value("admin"))
                .andExpect(jsonPath("$.data[0].accessKey").value("acce****3456"))
                .andExpect(jsonPath("$.data[0].secretKey").value("secr****7654"))
                .andExpect(jsonPath("$.data[0].admin").value(true));
    }

    @Test
    void createUserShouldReturnGeneratedCredentials() throws Exception {
        AclUserVO created = AclUserVO.builder()
                .id("user-1")
                .username("new-user")
                .accessKey("access-key-123456")
                .secretKey("secret-key-987654")
                .admin(false)
                .build();

        when(aclService.createUser(any(AclUserVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/acl/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new-user",
                                  "admin": true,
                                  "clusters": ["cluster-a"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessKey").value("access-key-123456"))
                .andExpect(jsonPath("$.data.secretKey").value("secret-key-987654"));

        ArgumentCaptor<AclUserVO> captor = ArgumentCaptor.forClass(AclUserVO.class);
        verify(aclService).createUser(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("new-user");
        assertThat(captor.getValue().isAdmin()).isTrue();
        assertThat(captor.getValue().getClusters()).containsExactly("cluster-a");
    }

    @Test
    void createUserShouldRejectMissingUsername() throws Exception {
        mockMvc.perform(post("/api/acl/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "admin": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("username is required"));

        verifyNoInteractions(aclService);
    }

    @Test
    void updateUserShouldReturnMaskedUpdatedUser() throws Exception {
        UpdateAclUserDTO input = new UpdateAclUserDTO();
        input.setId("user-1");
        input.setUsername("admin");
        input.setAdmin(false);
        AclUserVO updated = AclUserVO.builder()
                .id("user-1")
                .username("admin")
                .accessKey("acce****3456")
                .secretKey("secr****7654")
                .admin(false)
                .build();

        when(aclService.updateUser(any(UpdateAclUserDTO.class))).thenReturn(updated);

        mockMvc.perform(post("/api/acl/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.accessKey").value("acce****3456"))
                .andExpect(jsonPath("$.data.secretKey").value("secr****7654"))
                .andExpect(jsonPath("$.data.admin").value(false));
    }

    @Test
    void updateUserShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/acl/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(aclService);
    }

    @Test
    void deleteUserShouldPassValidatedRequest() throws Exception {
        mockMvc.perform(post("/api/acl/users/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(aclService).deleteUser("user-1");
    }

    @Test
    void deleteUserShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/acl/users/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(aclService);
    }

    // ── Plain access / cluster config inspection (PR-7) ──────────────

    @Test
    void examineClusterConfigShouldRequireClusterId() throws Exception {
        when(aclService.examineBrokerClusterAclConfig(any()))
                .thenThrow(new BusinessException(400, "clusterId is required"));

        mockMvc.perform(get("/api/acl/cluster-config"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("clusterId is required"));
    }

    @Test
    void examineClusterConfigShouldReturnConfig() throws Exception {
        AclClusterConfigVO config = AclClusterConfigVO.builder()
                .clusterId("cluster-a")
                .aclEnabled(true)
                .aclVersion("ACL 2.0")
                .globalWhiteRemoteAddresses(List.of("10.0.0.0/8"))
                .accounts(List.of(PlainAccessConfigVO.builder()
                        .accessKey("rocketmq-admin")
                        .admin(true)
                        .build()))
                .accountCount(1)
                .build();
        when(aclService.examineBrokerClusterAclConfig("cluster-a")).thenReturn(config);

        mockMvc.perform(get("/api/acl/cluster-config").param("clusterId", "cluster-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.clusterId").value("cluster-a"))
                .andExpect(jsonPath("$.data.aclEnabled").value(true))
                .andExpect(jsonPath("$.data.aclVersion").value("ACL 2.0"))
                .andExpect(jsonPath("$.data.accountCount").value(1))
                .andExpect(jsonPath("$.data.accounts[0].accessKey").value("rocketmq-admin"));

        verify(aclService).examineBrokerClusterAclConfig("cluster-a");
    }

    @Test
    void createUpdatePlainAccessConfigShouldRequireAccessKey() throws Exception {
        mockMvc.perform(post("/api/acl/plain-access-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("admin", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("accessKey is required"));

        verifyNoInteractions(aclService);
    }

    @Test
    void createUpdatePlainAccessConfigShouldReturnSavedConfig() throws Exception {
        PlainAccessConfigVO saved = PlainAccessConfigVO.builder()
                .accessKey("svc-x")
                .admin(false)
                .defaultTopicPerm("PUB")
                .topicPerms(List.of("order-*=PUB"))
                .build();
        when(aclService.createAndUpdatePlainAccessConfig(any(PlainAccessConfigVO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/acl/plain-access-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accessKey", "svc-x", "admin", false,
                                "defaultTopicPerm", "PUB", "topicPerms", List.of("order-*=PUB")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessKey").value("svc-x"))
                .andExpect(jsonPath("$.data.admin").value(false))
                .andExpect(jsonPath("$.data.topicPerms[0]").value("order-*=PUB"));

        ArgumentCaptor<PlainAccessConfigVO> captor = ArgumentCaptor.forClass(PlainAccessConfigVO.class);
        verify(aclService).createAndUpdatePlainAccessConfig(captor.capture());
        PlainAccessConfigVO request = captor.getValue();
        assertThat(request.getAccessKey()).isEqualTo("svc-x");
        assertThat(request.getDefaultTopicPerm()).isEqualTo("PUB");
        assertThat(request.getTopicPerms()).containsExactly("order-*=PUB");
    }
}
