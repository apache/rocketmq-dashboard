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
package org.apache.rocketmq.studio.ops.ai;

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiToolAuditServiceTest {

    @Mock
    private OperationAuditService operationAuditService;

    @InjectMocks
    private AiToolAuditService aiToolAuditService;

    @Test
    void recordSuccessShouldCaptureToolContextWithoutInputValues() {
        aiToolAuditService.recordSuccess("rmq.capabilities", Map.of(
                "cluster", "cluster-a",
                "secretToken", "token-value",
                "limit", 10));

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationAuditService).record(
                eq("EXECUTE_AI_TOOL"),
                eq("AI_TOOL"),
                eq("rmq.capabilities"),
                eq("cluster-a"),
                detailCaptor.capture(),
                eq("SUCCESS"),
                isNull());
        assertThat(detailCaptor.getValue()).contains("cluster", "limit", "secretToken")
                .doesNotContain("token-value");
    }

    @Test
    void recordFailureShouldCaptureErrorMessageAndAlternateClusterKey() {
        RuntimeException error = new RuntimeException("tool failed");

        aiToolAuditService.recordFailure("rmq.topic.list", Map.of("clusterId", "cluster-b"), error);

        verify(operationAuditService).record(
                eq("EXECUTE_AI_TOOL"),
                eq("AI_TOOL"),
                eq("rmq.topic.list"),
                eq("cluster-b"),
                eq("inputKeys=[clusterId]"),
                eq("FAILED"),
                eq("tool failed"));
    }

    @Test
    void auditPersistenceFailureShouldNotEscape() {
        doThrow(new RuntimeException("audit unavailable"))
                .when(operationAuditService)
                .record(any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> aiToolAuditService.recordSuccess("rmq.cluster.list", Map.of()))
                .doesNotThrowAnyException();
    }
}
