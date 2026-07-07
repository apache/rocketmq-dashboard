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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclServiceTest {

    @Mock
    private AclRepository aclRepository;

    @InjectMocks
    private AclService aclService;

    @Test
    void listRulesShouldReturnRulesFromRepository() {
        List<AclRuleVO> rules = List.of(
                AclRuleVO.builder().principal("user1").resource("topic-1").decision("ALLOW").build(),
                AclRuleVO.builder().principal("user2").resource("topic-2").decision("DENY").build()
        );
        when(aclRepository.findRules("cluster-1", "user1")).thenReturn(rules);

        List<AclRuleVO> result = aclService.listRules("cluster-1", "user1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPrincipal()).isEqualTo("user1");
        verify(aclRepository).findRules("cluster-1", "user1");
    }

    @Test
    void listRulesShouldPassNullFilters() {
        when(aclRepository.findRules(null, null)).thenReturn(List.of());

        List<AclRuleVO> result = aclService.listRules(null, null);

        assertThat(result).isEmpty();
        verify(aclRepository).findRules(null, null);
    }

    @Test
    void createRuleShouldSetIdAndTimestamp() {
        AclRuleVO input = AclRuleVO.builder()
                .principal("user1")
                .resource("topic-1")
                .resourceType("TOPIC")
                .decision("ALLOW")
                .build();

        when(aclRepository.saveRule(any(AclRuleVO.class))).thenAnswer(inv -> inv.getArgument(0));

        AclRuleVO result = aclService.createRule(input);

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getPrincipal()).isEqualTo("user1");
        assertThat(result.getResource()).isEqualTo("topic-1");
        verify(aclRepository).saveRule(any(AclRuleVO.class));
    }

    @Test
    void deleteRuleShouldDelegateToRepository() {
        aclService.deleteRule("rule-1");

        verify(aclRepository).deleteRule("rule-1");
    }

    @Test
    void listUsersShouldReturnAllUsers() {
        List<AclUserVO> users = List.of(
                AclUserVO.builder().username("admin").admin(true).build(),
                AclUserVO.builder().username("reader").admin(false).build()
        );
        when(aclRepository.findUsers()).thenReturn(users);

        List<AclUserVO> result = aclService.listUsers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("admin");
        verify(aclRepository).findUsers();
    }

    @Test
    void createUserShouldGenerateKeysAndSetTimestamp() {
        AclUserVO input = AclUserVO.builder()
                .username("newuser")
                .admin(false)
                .build();

        when(aclRepository.saveUser(any(AclUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        AclUserVO result = aclService.createUser(input);

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getAccessKey()).isNotBlank();
        assertThat(result.getSecretKey()).isNotBlank();
        assertThat(result.getAccessKey()).doesNotContain("-");
        assertThat(result.getSecretKey()).doesNotContain("-");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUsername()).isEqualTo("newuser");
        verify(aclRepository).saveUser(any(AclUserVO.class));
    }

    @Test
    void deleteUserShouldDelegateToRepository() {
        aclService.deleteUser("user-1");

        verify(aclRepository).deleteUser("user-1");
    }
}
