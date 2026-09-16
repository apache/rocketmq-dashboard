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
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclGetInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclRuleItem;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class AclGetToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private AclGetToolHandler handler;

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            new JsonMapper());

    @Test
    void executeShouldProjectTheRuleAndSatisfyTheOutputSchemaTest() {
        assertThat(handler.name()).isEqualTo("rmq.acl.get");
        AclRuleVO rule = AclRuleVO.builder()
                .id(7L)
                .principal("user-1")
                .resource("TopicA")
                .resourceType("TOPIC")
                .resourcePattern("PLAIN")
                .actions(List.of("PUB", "SUB"))
                .decision("GRANT")
                .scope("CLUSTER")
                .aclVersion("v2")
                .gmtCreate(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        when(aclService.getRule(eq("7"), eq("instance-a"))).thenReturn(rule);

        AclRuleItem item = handler.execute(
                new AclGetInput("instance-a", "7"), context("instance-a"));

        assertThat(item.id()).isEqualTo("7");
        assertThat(item.principal()).isEqualTo("user-1");
        assertThat(item.decision()).isEqualTo("GRANT");
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.acl.get"), item)).doesNotThrowAnyException();
        verify(aclService).getRule(eq("7"), eq("instance-a"));
    }

    @Test
    void executeShouldSatisfyTheOutputSchemaWhenOptionalFieldsAreNullTest() {
        when(aclService.getRule(eq("7"), eq("instance-a"))).thenReturn(AclRuleVO.builder()
                .id(7L)
                .principal("user-1")
                .resource("TopicA")
                .build());

        AclRuleItem item = handler.execute(
                new AclGetInput("instance-a", "7"), context("instance-a"));

        assertThat(item.resourceType()).isNull();
        assertThatCode(() -> validator.validateOutput(
                catalog.getDefinition("rmq.acl.get"), item)).doesNotThrowAnyException();
    }

    @Test
    void executeShouldPropagateTheNotFoundFailureOfAnUnknownIdTest() {
        when(aclService.getRule(eq("404"), eq("instance-a")))
                .thenThrow(new BusinessException(404, "ACL rule not found: 404"));

        assertThatThrownBy(() -> handler.execute(
                new AclGetInput("instance-a", "404"), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL rule not found");
    }
}
