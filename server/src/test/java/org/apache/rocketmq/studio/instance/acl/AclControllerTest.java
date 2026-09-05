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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AclController.class)
@AutoConfigureMockMvc(addFilters = false)
class AclControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AclService aclService;

    @MockBean
    private ApacheAclReadService apacheAclReadService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void capabilitiesShouldReturnApacheRemoteReadState() throws Exception {
        when(aclService.capabilities("instance-1"))
                .thenReturn(new AclCapabilitiesVO(1L,
                        org.apache.rocketmq.studio.common.domain.enums.InstanceVendor.APACHE,
                        org.apache.rocketmq.studio.common.domain.enums.InstanceType.DIRECT,
                        "APACHE_ACL2", true, false));

        mockMvc.perform(get("/api/acl/capabilities").param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instanceId").value(1))
                .andExpect(jsonPath("$.data.stateSource").value("APACHE_ACL2"))
                .andExpect(jsonPath("$.data.remoteReadSupported").value(true))
                .andExpect(jsonPath("$.data.remoteWriteSupported").value(false));
    }

    @Test
    void listRemoteRulesShouldRequireInstanceAndDelegateToApacheProvider() throws Exception {
        RemoteAclReadResult result = RemoteAclReadResult.builder()
                .source("APACHE_ACL2").policiesByBroker(Map.of()).failuresByBroker(Map.of()).build();
        when(apacheAclReadService.listRules("instance-1", null, null)).thenReturn(result);

        mockMvc.perform(get("/api/acl/remote/rules").param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("APACHE_ACL2"));

        verify(apacheAclReadService).listRules("instance-1", null, null);
    }

    @Test
    void listRulesShouldReturnRules() throws Exception {
        AclRuleVO rule = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("ALLOW")
                .build();
        rule.setId(1L);
        rule.setGmtCreate(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(aclService.listRules(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(PageResult.of(java.util.List.of(rule), 1, 1, 20));

        mockMvc.perform(get("/api/acl/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].principal").value("user1"))
                .andExpect(jsonPath("$.data.items[0].decision").value("ALLOW"));
    }

    @Test
    void listRulesShouldPassQueryParams() throws Exception {
        when(aclService.listRules(eq("user1"), eq("topic-a"), eq("namespace"), eq("DENY"),
                eq("1.0"), isNull(), eq(3), eq(5))).thenReturn(PageResult.empty(3, 5));

        mockMvc.perform(get("/api/acl/rules")
                        .param("principal", "user1")
                        .param("resource", "topic-a")
                        .param("scope", "namespace")
                        .param("decision", "DENY")
                        .param("aclVersion", "1.0")
                        .param("page", "3")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(aclService).listRules(eq("user1"), eq("topic-a"), eq("namespace"), eq("DENY"),
                eq("1.0"), isNull(), eq(3), eq(5));
    }

    @Test
    void listRulesShouldRejectPageSizeAboveTheInventoryLimit() throws Exception {
        when(aclService.listRules(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(1), eq(101))).thenThrow(new BusinessException(400,
                "page must be >= 1 and pageSize must be between 1 and 100"));

        mockMvc.perform(get("/api/acl/rules")
                        .param("page", "1")
                        .param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("page must be >= 1 and pageSize must be between 1 and 100"));

        verify(aclService).listRules(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(1), eq(101));
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
        created.setId(2L);
        created.setGmtCreate(LocalDateTime.of(2026, 7, 8, 12, 0));

        when(aclService.createRule(any(AclRuleVO.class), isNull())).thenReturn(created);

        mockMvc.perform(post("/api/acl/rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
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
                .id(1L)
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("DENY")
                .build();

        when(aclService.updateRule(any(AclRuleVO.class), isNull())).thenReturn(input);

        mockMvc.perform(post("/api/acl/rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.decision").value("DENY"));
    }

    @Test
    void updateRuleShouldAcceptTencentRoleNameIdentifier() throws Exception {
        AclRuleVO updated = AclRuleVO.builder()
                .principal("reader-role")
                .resource("*")
                .resourceType("Cluster")
                .resourcePattern("LITERAL")
                .actions(List.of("SUB"))
                .decision("ALLOW")
                .scope("cluster")
                .aclVersion("1.0")
                .build();
        when(aclService.updateRule(any(AclRuleVO.class), eq("tencent-rmq"))).thenReturn(updated);

        mockMvc.perform(post("/api/acl/rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "reader-role",
                                  "principal": "reader-role",
                                  "resource": "*",
                                  "resourceType": "Cluster",
                                  "resourcePattern": "LITERAL",
                                  "actions": ["SUB"],
                                  "decision": "ALLOW",
                                  "scope": "cluster",
                                  "instanceId": "tencent-rmq"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.principal").value("reader-role"))
                .andExpect(jsonPath("$.data.resource").value("*"));

        ArgumentCaptor<AclRuleVO> captor = ArgumentCaptor.forClass(AclRuleVO.class);
        verify(aclService).updateRule(captor.capture(), eq("tencent-rmq"));
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getPrincipal()).isEqualTo("reader-role");
    }

    @Test
    void updateRuleShouldReturnNotFoundForUnknownId() throws Exception {
        AclRuleVO input = AclRuleVO.builder()
                .id(999L)
                .principal("user1")
                .resource("topic-1")
                .decision("DENY")
                .build();
        when(aclService.updateRule(any(AclRuleVO.class), isNull()))
                .thenThrow(new BusinessException(404, "ACL rule not found: 999"));

        mockMvc.perform(post("/api/acl/rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("ACL rule not found: 999"));
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
                        .content(objectMapper.writeValueAsString(Map.of("id", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(aclService).deleteRule(eq("1"), isNull());
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
        user.setId(1L);
        user.setGmtCreate(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(aclService.listUsers(isNull())).thenReturn(List.of(user));

        mockMvc.perform(get("/api/acl/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].username").value("admin"))
                .andExpect(jsonPath("$.data[0].accessKey").value("acce****3456"))
                .andExpect(jsonPath("$.data[0].secretKey").value("secr****7654"))
                .andExpect(jsonPath("$.data[0].admin").value(true));
    }

    @Test
    void getUserCredentialsShouldDisableResponseCaching() throws Exception {
        AclUserVO credentials = AclUserVO.builder()
                .id(1L)
                .accessKey("access-key")
                .secretKey("secret-key")
                .build();
        when(aclService.getUserCredentials(eq("1"), isNull())).thenReturn(credentials);

        mockMvc.perform(get("/api/acl/users/1/credentials"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void createUserShouldReturnGeneratedCredentials() throws Exception {
        AclUserVO created = AclUserVO.builder()
                .id(1L)
                .username("new-user")
                .accessKey("access-key-123456")
                .secretKey("secret-key-987654")
                .admin(false)
                .build();

        when(aclService.createUser(any(AclUserVO.class), isNull())).thenReturn(created);

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
        verify(aclService).createUser(captor.capture(), isNull());
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
        input.setId("1");
        input.setUsername("admin");
        input.setAdmin(false);
        AclUserVO updated = AclUserVO.builder()
                .id(1L)
                .username("admin")
                .accessKey("acce****3456")
                .secretKey("secr****7654")
                .admin(false)
                .build();

        when(aclService.updateUser(any(UpdateAclUserDTO.class), isNull())).thenReturn(updated);

        mockMvc.perform(post("/api/acl/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.accessKey").value("acce****3456"))
                .andExpect(jsonPath("$.data.secretKey").value("secr****7654"))
                .andExpect(jsonPath("$.data.admin").value(false));
    }

    @Test
    void updateUserShouldAcceptTencentRoleNameIdentifier() throws Exception {
        AclUserVO updated = AclUserVO.builder()
                .username("reader-role")
                .accessKey("acce****3456")
                .secretKey("secr****7654")
                .admin(false)
                .permRead(true)
                .permWrite(false)
                .build();
        when(aclService.updateUser(any(UpdateAclUserDTO.class), eq("tencent-rmq"))).thenReturn(updated);

        mockMvc.perform(post("/api/acl/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "reader-role",
                                  "username": "reader-role",
                                  "permRead": true,
                                  "permWrite": false,
                                  "instanceId": "tencent-rmq"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("reader-role"))
                .andExpect(jsonPath("$.data.permRead").value(true));

        ArgumentCaptor<UpdateAclUserDTO> captor = ArgumentCaptor.forClass(UpdateAclUserDTO.class);
        verify(aclService).updateUser(captor.capture(), eq("tencent-rmq"));
        assertThat(captor.getValue().getId()).isEqualTo("reader-role");
        assertThat(captor.getValue().getUsername()).isEqualTo("reader-role");
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
                        .content(objectMapper.writeValueAsString(Map.of("id", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(aclService).deleteUser(eq("1"), isNull());
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
                .whiteRemoteAddress("10.0.1.*")
                .topicPerms(List.of("order-*=PUB"))
                .build();
        when(aclService.createAndUpdatePlainAccessConfig(any(PlainAccessConfigVO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/acl/plain-access-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accessKey", "svc-x", "admin", false,
                                "defaultTopicPerm", "PUB",
                                "whiteRemoteAddress", "10.0.1.*",
                                "topicPerms", List.of("order-*=PUB")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessKey").value("svc-x"))
                .andExpect(jsonPath("$.data.admin").value(false))
                .andExpect(jsonPath("$.data.whiteRemoteAddress").value("10.0.1.*"))
                .andExpect(jsonPath("$.data.topicPerms[0]").value("order-*=PUB"));

        ArgumentCaptor<PlainAccessConfigVO> captor = ArgumentCaptor.forClass(PlainAccessConfigVO.class);
        verify(aclService).createAndUpdatePlainAccessConfig(captor.capture());
        PlainAccessConfigVO request = captor.getValue();
        assertThat(request.getAccessKey()).isEqualTo("svc-x");
        assertThat(request.getDefaultTopicPerm()).isEqualTo("PUB");
        assertThat(request.getWhiteRemoteAddress()).isEqualTo("10.0.1.*");
        assertThat(request.getTopicPerms()).containsExactly("order-*=PUB");
    }

    @Test
    void createUpdatePlainAccessConfigShouldReturnBadRequestForInvalidWhiteRemoteAddress() throws Exception {
        when(aclService.createAndUpdatePlainAccessConfig(any(PlainAccessConfigVO.class)))
                .thenThrow(new BusinessException(400,
                        "whiteRemoteAddress is not a valid plain ACL address expression: invalid"));

        mockMvc.perform(post("/api/acl/plain-access-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accessKey", "svc-x",
                                "whiteRemoteAddress", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("whiteRemoteAddress is not a valid plain ACL address expression: invalid"));

        ArgumentCaptor<PlainAccessConfigVO> captor = ArgumentCaptor.forClass(PlainAccessConfigVO.class);
        verify(aclService).createAndUpdatePlainAccessConfig(captor.capture());
        assertThat(captor.getValue().getWhiteRemoteAddress()).isEqualTo("invalid");
    }
    @Test
    void listUsersPageShouldForwardFiltersAndPaging() throws Exception {
        AclUserVO user = AclUserVO.builder().id(7L).username("operator").admin(false).build();
        when(aclService.pageUsers("inst-1", 2, 20, "oper"))
                .thenReturn(PageResult.of(List.of(user), 21, 2, 20));

        mockMvc.perform(get("/api/acl/users/page")
                        .param("instanceId", "inst-1")
                        .param("page", "2")
                        .param("pageSize", "20")
                        .param("keyword", "oper"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].username").value("operator"))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2));

        verify(aclService).pageUsers("inst-1", 2, 20, "oper");
    }

    @Test
    void listUsersPageShouldUseBoundedDefaults() throws Exception {
        when(aclService.pageUsers(null, 1, 20, null))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/acl/users/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1));

        verify(aclService).pageUsers(null, 1, 20, null);
    }

}
