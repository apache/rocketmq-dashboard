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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserListToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private UserListToolHandler handler;

    @Test
    void executeShouldUseTheBoundedAclUserPageAndExposeItsMetadataTest() {
        AclUserVO user = AclUserVO.builder()
                .id(11L)
                .username("orders")
                .admin(false)
                .clusters(List.of("DefaultCluster"))
                .build();
        when(aclService.pageUsers(eq("instance-a"), eq(2), eq(20), isNull()))
                .thenReturn(PageResult.of(List.of(user), 21, 2, 20));

        PageOutput<?> result = handler.execute(
                new UserListInput("instance-a", null,
                        new PageRequest(2, 20)),
                context("instance-a"));

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(21);
        assertThat(result.items()).hasSize(1);
        ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
        ToolSchemaValidator validator = new ToolSchemaValidator(
                catalog,
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
                new JsonMapper());
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.list"), result)).doesNotThrowAnyException();
        verify(aclService).pageUsers(eq("instance-a"), eq(2), eq(20), isNull());
    }

    @Test
    void executeShouldAcceptAUserWithoutClusterBindingsTest() {
        AclUserVO user = AclUserVO.builder()
                .id(12L)
                .username("global-user")
                .admin(true)
                .build();
        when(aclService.pageUsers(eq("instance-a"), eq(1), eq(20), isNull()))
                .thenReturn(PageResult.of(List.of(user), 1, 1, 20));

        PageOutput<?> result = handler.execute(
                new UserListInput("instance-a", null, null), context("instance-a"));

        assertThat(result.items()).hasSize(1);
        ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
        ToolSchemaValidator validator = new ToolSchemaValidator(
                catalog,
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
                new JsonMapper());
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.list"), result)).doesNotThrowAnyException();
    }
}
