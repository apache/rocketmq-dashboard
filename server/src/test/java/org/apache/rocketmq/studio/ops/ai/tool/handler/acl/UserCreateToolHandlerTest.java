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
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserCreateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class UserCreateToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private UserCreateToolHandler handler;

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            new JsonMapper());

    @Test
    void executeShouldProjectCreatedUser() {
        assertThat(handler.name()).isEqualTo("rmq.user.create");
        AclUserVO created = AclUserVO.builder()
                .id(11L)
                .username("orders")
                .admin(false)
                .build();
        when(aclService.createUser(any(AclUserVO.class), eq("cluster-1"))).thenReturn(created);

        AclUserItem item = handler.execute(
                new UserCreateInput("cluster-1", "orders", false, null), context("cluster-1"));

        assertThat(item.id()).isEqualTo("11");
        assertThat(item.username()).isEqualTo("orders");
        assertThat(item.admin()).isFalse();
        assertThat(item.clusters()).isNull();
    }

    /**
     * Regression for §14 defect 2: a locally provisioned user carries no {@code clusters}, so
     * {@link AclUserItem}'s {@code @JsonInclude(NON_NULL)} omits the field. Before the fix the
     * {@code rmq.user.create} result schema still required {@code clusters}, so the tool reported
     * a false failure even though the user had actually been written.
     */
    @Test
    void executedOutputShouldSatisfySchemaWhenUserHasNoClusters() {
        AclUserVO created = AclUserVO.builder()
                .id(11L)
                .username("orders")
                .admin(true)
                .build();
        when(aclService.createUser(any(AclUserVO.class), eq("cluster-1"))).thenReturn(created);

        AclUserItem result = handler.execute(
                new UserCreateInput("cluster-1", "orders", true, null), context("cluster-1"));
        ToolPlan plan = ToolPlan.builder("create user 'orders' in instance 'cluster-1.'").build();
        MutationOutput<AclUserItem> output = new MutationOutput<>(
                MutationOutput.Status.EXECUTED, "cluster-1", plan, null, result);

        assertThat(result.clusters()).isNull();
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.create"), output)).doesNotThrowAnyException();
    }
}
