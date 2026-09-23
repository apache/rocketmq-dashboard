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

import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class UserUpdateToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private UserUpdateToolHandler handler;

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            new JsonMapper());

    @Test
    void executeShouldDelegatePartialUpdateAndProjectSafeUser() {
        AclUserVO updated = AclUserVO.builder()
                .id(11L)
                .username("orders-admin")
                .admin(true)
                .clusters(List.of("cluster-a"))
                .accessKey("access-key")
                .secretKey("secret-key")
                .build();
        when(aclService.updateUser(any(), eq("instance-1"))).thenReturn(updated);

        UserUpdateInput input = new UserUpdateInput(
                "instance-1", "11", "orders-admin", true, List.of("cluster-a"));
        var result = handler.execute(input, context("instance-1"));

        assertThat(handler.name()).isEqualTo("rmq.user.update");
        assertThat(result.id()).isEqualTo("11");
        assertThat(result.username()).isEqualTo("orders-admin");
        assertThat(result.admin()).isTrue();
        assertThat(result.clusters()).containsExactly("cluster-a");
        ArgumentCaptor<org.apache.rocketmq.studio.instance.acl.UpdateAclUserDTO> update =
                ArgumentCaptor.forClass(org.apache.rocketmq.studio.instance.acl.UpdateAclUserDTO.class);
        verify(aclService).updateUser(update.capture(), eq("instance-1"));
        assertThat(update.getValue().getId()).isEqualTo("11");
        assertThat(update.getValue().getUsername()).isEqualTo("orders-admin");
        assertThat(update.getValue().getAdmin()).isTrue();
        assertThat(update.getValue().getClusters()).containsExactly("cluster-a");
    }

    @Test
    void previewShouldPreserveOmittedFields() {
        AclUserVO existing = AclUserVO.builder()
                .id(11L)
                .username("orders")
                .admin(false)
                .clusters(List.of("cluster-a"))
                .build();
        when(aclService.getUser("11", "instance-1")).thenReturn(existing);

        ToolExecutionContext context = context("instance-1");
        var plan = handler.preview(
                new UserUpdateInput("instance-1", "11", "orders-renamed", null, null), context);

        assertThat(plan.before()).containsEntry("username", "orders");
        assertThat(plan.after()).containsEntry("username", "orders-renamed");
        assertThat(plan.after()).containsEntry("admin", false);
        assertThat(plan.after()).containsEntry("clusters", List.of("cluster-a"));
    }

    @Test
    void TencentRoleOutputShouldSatisfySchemaWithoutNumericId() {
        AclUserVO updated = AclUserVO.builder()
                .username("reader-role")
                .admin(false)
                .clusters(List.of("tencent-rmq"))
                .permRead(true)
                .permWrite(false)
                .build();
        when(aclService.updateUser(any(), eq("tencent-rmq"))).thenReturn(updated);

        AclUserItem result = handler.execute(
                new UserUpdateInput("tencent-rmq", "reader-role", null, null, null),
                context("tencent-rmq"));
        MutationOutput<AclUserItem> output = new MutationOutput<>(
                MutationOutput.Status.EXECUTED,
                "tencent-rmq",
                ToolPlan.builder("update user 'reader-role' in instance 'tencent-rmq.'").build(),
                null,
                result);

        assertThat(result.id()).isNull();
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.update"), output)).doesNotThrowAnyException();
    }
}
