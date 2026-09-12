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
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class AclCreateToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private AclCreateToolHandler handler;

    @ParameterizedTest
    @MethodSource("configurations")
    void createPreviewsExactlyTheConfigurationSentToTheService(Map<String, Object> configuration) {
        assertThat(handler.name()).isEqualTo("rmq.acl.create");
        Map<String, Object> input = new LinkedHashMap<>(configuration);
        input.put("cluster", "cluster-1");
        input.put("dry_run", true);
        ToolExecutionContext execution = context("cluster-1", input);
        ToolPlan plan = handler.preview(
                execution.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput.class),
                execution);

        assertThat(plan.before()).isEmpty();
        assertThat(plan.after()).containsExactlyInAnyOrderEntriesOf(configuration);
        verifyNoInteractions(aclService);

        AclRuleVO created = AclRuleVO.builder()
                .id(1L)
                .principal("user-1")
                .resource("TopicA")
                .decision("GRANT")
                .build();
        when(aclService.createRule(any(AclRuleVO.class), eq("cluster-1")))
                .thenReturn(created);

        AclRuleVO result = handler.execute(execution.convertInput(AclMutationInput.class), execution);

        assertThat(result).isSameAs(created);

        ArgumentCaptor<AclRuleVO> captor = ArgumentCaptor.forClass(AclRuleVO.class);
        verify(aclService).createRule(captor.capture(), eq("cluster-1"));
        assertThat(captor.getValue()).isEqualTo(plan.after(AclRuleVO.class));
    }

    private static Stream<Map<String, Object>> configurations() {
        return Stream.of(
                Map.of("principal", "user-1", "resource", "TopicA",
                        "actions", List.of("PUB"), "decision", "GRANT"),
                Map.of("principal", "user-1", "resource", "TopicA",
                        "resourceType", "TOPIC", "resourcePattern", "LITERAL",
                        "actions", List.of("PUB", "SUB"), "decision", "GRANT",
                        "scope", "CLUSTER", "aclVersion", "2"));
    }
}
