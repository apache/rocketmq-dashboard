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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolAccessPolicyTest {

    @Mock
    private ToolCatalog catalog;

    @InjectMocks
    private ToolAccessPolicy policy;

    private static ToolDefinition definition(String name, String riskLevel, String permission) {
        return new ToolDefinition(name, null, name, riskLevel, permission,
                List.of(), Map.of(), Map.of(), null, false, null);
    }

    @Test
    void recognizesToolExecutionPaths() {
        assertThat(policy.isToolExecutionPath("/api/ai/tools/rmq.topic.list/execute")).isTrue();
        assertThat(policy.isToolExecutionPath("/api/ai/tools/rmq.topic.list")).isFalse();
        assertThat(policy.isToolExecutionPath("/api/ai/other/rmq.topic.list/execute")).isFalse();
        assertThat(policy.isToolExecutionPath(null)).isFalse();
        // A blank tool name still sits on the execution path but resolves to no tool.
        assertThat(policy.isToolExecutionPath("/api/ai/tools/execute")).isTrue();
        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/execute")).isFalse();
    }

    @Test
    void grantsReaderAccessToKnownLowRiskReadOnlyTool() {
        when(catalog.find("rmq.topic.list")).thenReturn(
                Optional.of(definition("rmq.topic.list", "L1", "topic:read")));

        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.topic.list/execute")).isTrue();
    }

    @Test
    void deniesReaderAccessToDenyListedTool() {
        when(catalog.find("rmq.message.trace")).thenReturn(
                Optional.of(definition("rmq.message.trace", "L1", "message:read")));

        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.message.trace/execute")).isFalse();
    }

    @Test
    void deniesReaderAccessToWriteTool() {
        when(catalog.find("rmq.topic.create")).thenReturn(
                Optional.of(definition("rmq.topic.create", "L2", "topic:write")));

        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.topic.create/execute")).isFalse();
    }

    @Test
    void decodesPercentEncodedToolNames() {
        when(catalog.find("rmq.topic.list")).thenReturn(
                Optional.of(definition("rmq.topic.list", "L1", "topic:read")));

        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.topic%2Elist/execute")).isTrue();
    }

    @Test
    void malformedPercentSequenceFailsClosedWithoutThrowing() {
        assertThatCode(() -> policy.isReaderAccessiblePath(
                "/api/ai/tools/rmq.topic%2zlist/execute"))
                .doesNotThrowAnyException();
        assertThat(policy.isReaderAccessiblePath(
                "/api/ai/tools/rmq.topic%2zlist/execute")).isFalse();
    }

    @Test
    void invalidUtf8ToolNameFailsClosedWithoutThrowing() {
        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/%FF/execute")).isFalse();
    }

    @Test
    void unknownToolNameFailsClosed() {
        when(catalog.find("rmq.unknown.tool")).thenReturn(Optional.empty());

        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.unknown.tool/execute")).isFalse();
    }

    @Test
    void authorizesAdminWithoutCatalogLookup() {
        org.apache.rocketmq.studio.auth.AuthenticatedUserContext
                .setUser("admin-user", true);
        try {
            assertThatCode(() -> policy.authorizeCurrentUser(
                    definition("rmq.topic.create", "L2", "topic:write")))
                    .doesNotThrowAnyException();
        } finally {
            org.apache.rocketmq.studio.auth.AuthenticatedUserContext.clear();
        }
    }

    @Test
    void rejectsReaderForWriteTool() {
        org.apache.rocketmq.studio.auth.AuthenticatedUserContext
                .setUser("reader-user", false);
        try {
            assertThatThrownBy(() -> policy.authorizeCurrentUser(
                    definition("rmq.topic.create", "L2", "topic:write")))
                    .isInstanceOf(BusinessException.class);
        } finally {
            org.apache.rocketmq.studio.auth.AuthenticatedUserContext.clear();
        }
    }
}
