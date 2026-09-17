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
package org.apache.rocketmq.studio.ops.ai.tool.handler.audit;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.AuditItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.AuditListInput;
import org.apache.rocketmq.studio.ops.audit.AuditRecordVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditListToolHandlerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditListToolHandler handler;

    @Test
    void executeShouldQueryTheWholeDeploymentAndKeepTheInstanceAttributionTest() {
        assertThat(handler.name()).isEqualTo("rmq.audit.list");
        AuditRecordVO record = AuditRecordVO.builder()
                .timestamp(LocalDateTime.of(2026, 9, 14, 10, 30))
                .operator("rocketmq")
                .operationType("DELETE_TOPIC")
                .resourceType("TOPIC")
                .target("rmq.topic.delete")
                .clusterId("instance-b")
                .result("SUCCESS")
                .build();
        record.setId(42L);
        when(auditService.queryLogs(eq(1), eq(20), eq("topic"), isNull(), eq("DELETE"), eq("TOPIC"),
                isNull(), isNull(), eq(false), eq("2026-09-01"), eq("2026-09-14"), eq("SUCCESS")))
                .thenReturn(PageResult.of(List.of(record), 1, 1, 20));

        PageOutput<AuditItem> result = handler.execute(new AuditListInput(
                        "topic", "DELETE", "TOPIC", "2026-09-01", "2026-09-14", "SUCCESS",
                        new PageRequest(1, 20)),
                context("instance-a"));

        assertThat(result.total()).isEqualTo(1L);
        AuditItem item = result.items().getFirst();
        assertThat(item.id()).isEqualTo(42L);
        assertThat(item.operationType()).isEqualTo("DELETE_TOPIC");
        // Rows written by another Instance stay visible: cluster_id carries the attribution.
        assertThat(item.clusterId()).isEqualTo("instance-b");
        verify(auditService).queryLogs(eq(1), eq(20), eq("topic"), isNull(), eq("DELETE"), eq("TOPIC"),
                isNull(), isNull(), eq(false), eq("2026-09-01"), eq("2026-09-14"), eq("SUCCESS"));
    }
}
