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
package org.apache.rocketmq.studio.ops.ai.tool.handler.acl;

import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class AclUpdateToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private AclUpdateToolHandler handler;

    @Test
    void applyShouldUpdateRule() {
        assertThat(handler.name()).isEqualTo("rmq.acl.update");
        AclRuleVO updated = AclRuleVO.builder()
                .id(1L)
                .principal("user-1")
                .resource("TopicB")
                .decision("DENY")
                .build();
        when(aclService.getRule("1", "cluster-1")).thenReturn(AclRuleVO.builder()
                .id(1L)
                .principal("user-1")
                .resource("TopicA")
                .build());
        when(aclService.updateRule(any(AclRuleVO.class), eq("cluster-1")))
                .thenReturn(updated);

        Object result = handler.execute(new AclMutationInput(
                "cluster-1", "1", "user-1", "TopicB", null, null,
                null, "DENY", null), context("cluster-1"));

        assertThat(result).isInstanceOf(AclRuleVO.class);
        AclRuleVO rule = (AclRuleVO) result;
        assertThat(rule.getResource()).isEqualTo("TopicB");

        ArgumentCaptor<AclRuleVO> captor = ArgumentCaptor.forClass(AclRuleVO.class);
        verify(aclService).updateRule(captor.capture(), eq("cluster-1"));
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    /**
     * Providers whose ACL rules have no numeric database id are addressed by their principal:
     * AclService.getRule resolves a role name and AclService.updateRule never asks Tencent for an
     * id (TencentAclService.updateRule maps the rule's principal onto ModifyRole). The handler must
     * therefore resolve the addressed rule instead of demanding a number, or an ACL rule of such an
     * instance can never be updated through the tool while get, list and delete all work.
     */
    @Test
    void applyShouldUpdateARuleAddressedByPrincipalTest() {
        AclRuleVO existing = AclRuleVO.builder()
                .principal("GID_role")
                .resource("TopicA")
                .decision("GRANT")
                .build();
        AclRuleVO updated = AclRuleVO.builder()
                .principal("GID_role")
                .resource("TopicB")
                .decision("DENY")
                .build();
        when(aclService.getRule("GID_role", "cluster-1")).thenReturn(existing);
        when(aclService.updateRule(any(AclRuleVO.class), eq("cluster-1")))
                .thenReturn(updated);

        Object result = handler.execute(new AclMutationInput(
                "cluster-1", "GID_role", "GID_role", "TopicB", null, null,
                null, "DENY", null), context("cluster-1"));

        assertThat(result).isSameAs(updated);
        ArgumentCaptor<AclRuleVO> captor = ArgumentCaptor.forClass(AclRuleVO.class);
        verify(aclService).updateRule(captor.capture(), eq("cluster-1"));
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getPrincipal()).isEqualTo("GID_role");
        assertThat(captor.getValue().getResource()).isEqualTo("TopicB");
        assertThat(captor.getValue().getDecision()).isEqualTo("DENY");
    }

    @Test
    void planShouldPreviewARuleAddressedByPrincipalTest() {
        AclRuleVO existing = AclRuleVO.builder()
                .principal("GID_role")
                .resource("TopicA")
                .decision("GRANT")
                .build();
        when(aclService.getRule("GID_role", "cluster-1")).thenReturn(existing);

        ToolPlan plan = handler.preview(new AclMutationInput(
                "cluster-1", "GID_role", "GID_role", "TopicB", null, null,
                null, "DENY", null), context("cluster-1"));

        assertThat(plan.before()).containsEntry("principal", "GID_role");
        assertThat(plan.after())
                .containsEntry("principal", "GID_role")
                .containsEntry("resource", "TopicB");
    }

    @Test
    void applyShouldReportAnUnknownRuleFromTheServiceTest() {
        when(aclService.getRule("999", "cluster-1"))
                .thenThrow(new BusinessException(404, "ACL rule not found: 999"));

        assertThatThrownBy(() -> handler.execute(new AclMutationInput(
                "cluster-1", "999", "user-1", "TopicB", null, null,
                null, "DENY", null), context("cluster-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL rule not found");
    }
}
