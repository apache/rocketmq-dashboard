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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserGetInput;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class UserGetToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private UserGetToolHandler handler;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog, objectMapper, new JsonMapper());

    @Test
    void executeShouldProjectTheUserWithoutCredentialsTest() {
        assertThat(handler.name()).isEqualTo("rmq.user.get");
        when(aclService.getUser(eq("11"), eq("instance-a"))).thenReturn(AclUserVO.builder()
                .id(11L)
                .username("orders")
                .admin(true)
                .clusters(List.of("DefaultCluster"))
                .accessKey("ak-must-not-leak")
                .secretKey("sk-must-not-leak")
                .build());

        AclUserItem item = handler.execute(
                new UserGetInput("instance-a", "11"), context("instance-a"));

        assertThat(item.id()).isEqualTo("11");
        assertThat(item.username()).isEqualTo("orders");
        assertThat(item.admin()).isTrue();
        assertThat(item.clusters()).containsExactly("DefaultCluster");
        assertThat(objectMapper.valueToTree(item).toString())
                .doesNotContain("ak-must-not-leak")
                .doesNotContain("sk-must-not-leak")
                .doesNotContain("accessKey")
                .doesNotContain("secretKey");
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.get"), item)).doesNotThrowAnyException();
        verify(aclService).getUser(eq("11"), eq("instance-a"));
        verify(aclService, never()).getUserCredentials(eq("11"), eq("instance-a"));
        verifyNoMoreInteractions(aclService);
    }

    @Test
    void executeShouldSatisfyTheOutputSchemaWhenTheUserHasNoClustersTest() {
        when(aclService.getUser(eq("11"), eq("instance-a"))).thenReturn(AclUserVO.builder()
                .id(11L)
                .username("orders")
                .admin(false)
                .build());

        AclUserItem item = handler.execute(
                new UserGetInput("instance-a", "11"), context("instance-a"));

        assertThat(item.clusters()).isNull();
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.get"), item)).doesNotThrowAnyException();
    }

    @Test
    void executeShouldPropagateTheNotFoundFailureOfAnUnknownIdTest() {
        when(aclService.getUser(eq("404"), eq("instance-a")))
                .thenThrow(new BusinessException(404, "ACL user not found: 404"));

        assertThatThrownBy(() -> handler.execute(
                new UserGetInput("instance-a", "404"), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL user not found");
    }

    /**
     * A role-backed instance (Tencent) has no numeric primary key for its ACL users
     * either: the projection is built from the cloud role, so {@code id} is {@code null}
     * and the record omits it. The output schema requires {@code id}, so reading one user
     * aborted the tool call instead of returning the role.
     */
    @Test
    void executeShouldIdentifyAUserThatHasNoNumericId() {
        when(aclService.getUser(eq("role-a"), eq("instance-a"))).thenReturn(AclUserVO.builder()
                .username("role-a")
                .admin(false)
                .clusters(List.of("rmq-xxx"))
                .build());

        AclUserItem item = handler.execute(
                new UserGetInput("instance-a", "role-a"), context("instance-a"));

        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.user.get"), item)).doesNotThrowAnyException();
        assertThat(item.id()).isEqualTo("role-a");
        assertThat(item.username()).isEqualTo("role-a");
    }
}
