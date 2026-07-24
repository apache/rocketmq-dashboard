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

package com.rocketmq.studio.instance.acl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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
    void listUsersShouldReturnAllUsers() throws Exception {
        AclUserVO user = AclUserVO.builder()
                .username("admin")
                .accessKey("ak123")
                .secretKey("sk456")
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
                .andExpect(jsonPath("$.data[0].admin").value(true));
    }

    @Test
    void updateUserShouldReturnUpdatedUser() throws Exception {
        AclUserVO input = AclUserVO.builder()
                .id("user-1")
                .username("admin")
                .accessKey("ak123")
                .secretKey("sk456")
                .admin(false)
                .build();

        when(aclService.updateUser(any(AclUserVO.class))).thenReturn(input);

        mockMvc.perform(post("/api/acl/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.admin").value(false));
    }
}
