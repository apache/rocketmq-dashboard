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

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Unit tests for {@link ToolAccessPolicy}: path-shape recognition, the reader exposure gate
 * (low-risk read-only minus the remoting-steering deny list), and admin/user authorization.
 */
@ExtendWith(MockitoExtension.class)
class ToolAccessPolicyTest {

    @Mock
    private ToolCatalog catalog;

    @InjectMocks
    private ToolAccessPolicy policy;

    @AfterEach
    void clearUserContext() {
        AuthenticatedUserContext.clear();
    }

    private static ToolDefinition tool(String name, String riskLevel, String permission) {
        return new ToolDefinition(name, new ToolDefinition.Cli("topic", "list"), "desc",
                riskLevel, permission, List.of(), Map.of(), Map.of(), null, false, null);
    }

    @Test
    void recognizesOnlyToolExecutionPaths() {
        assertThat(policy.isToolExecutionPath("/api/ai/tools/rmq.cluster.list/execute")).isTrue();
        assertThat(policy.isToolExecutionPath("/api/ai/tools/rmq.cluster.list")).isFalse();
        assertThat(policy.isToolExecutionPath("/api/ai/other/execute")).isFalse();
        assertThat(policy.isToolExecutionPath(null)).isFalse();
    }

    @Test
    void exposesOnlyLowRiskReadOnlyToolsToReaders() {
        assertThat(policy.isReaderAccessible(tool("rmq.cluster.list", "L1", "cluster:read"))).isTrue();
        assertThat(policy.isReaderAccessible(tool("rmq.topic.write", "L1", "cluster:write"))).isFalse();
        assertThat(policy.isReaderAccessible(tool("rmq.group.delete", "L2", "cluster:read"))).isFalse();
        assertThat(policy.isReaderAccessible(null)).isFalse();
    }

    @Test
    void withholdsDenyListedReadToolsFromReaders() {
        assertThat(policy.isReaderAccessible(tool("rmq.message.query", "L1", "cluster:read"))).isFalse();
        assertThat(policy.isReaderAccessible(tool("rmq.message.trace", "L1", "cluster:read"))).isFalse();
    }

    @Test
    void resolvesReaderAccessFromTheRequestPath() {
        when(catalog.find("rmq.cluster.list")).thenReturn(
                Optional.of(tool("rmq.cluster.list", "L1", "cluster:read")));
        when(catalog.find("rmq.message.query")).thenReturn(
                Optional.of(tool("rmq.message.query", "L1", "cluster:read")));

        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.cluster.list/execute")).isTrue();
        // URL-encoded tool names are decoded before catalog lookup.
        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.cluster%2Elist/execute")).isTrue();
        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.message.query/execute")).isFalse();
        assertThat(policy.isReaderAccessiblePath("/api/ai/tools/rmq.unknown/execute")).isFalse();
        assertThat(policy.isReaderAccessiblePath("/api/ai/other/execute")).isFalse();
    }

    @Test
    void allowsReadersOnlyForToolsTheyMayTouch() {
        assertThatCode(() -> policy.authorizeCurrentUser(tool("rmq.cluster.list", "L1", "cluster:read")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonAdminUsersForRestrictedTools() {
        AuthenticatedUserContext.setUser("reader", false);

        assertThatThrownBy(() -> policy.authorizeCurrentUser(tool("rmq.message.query", "L1", "cluster:read")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(403));
    }

    @Test
    void allowsAdminsForRestrictedTools() {
        AuthenticatedUserContext.setUser("admin", true);

        assertThatCode(() -> policy.authorizeCurrentUser(tool("rmq.message.query", "L1", "cluster:read")))
                .doesNotThrowAnyException();
    }
}
