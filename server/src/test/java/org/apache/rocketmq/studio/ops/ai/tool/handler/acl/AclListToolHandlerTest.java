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

import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclRuleItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class AclListToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private AclListToolHandler handler;

    @Test
    void executeShouldListRulesAndProjectItems() {
        assertThat(handler.name()).isEqualTo("rmq.acl.list");
        AclRuleVO rule = AclRuleVO.builder()
                .id(1L)
                .principal("user-1")
                .resource("TopicA")
                .resourceType("TOPIC")
                .resourcePattern("PLAIN")
                .actions(List.of("PUB", "SUB"))
                .decision("GRANT")
                .scope("CLUSTER")
                .aclVersion("v1")
                .gmtCreate(LocalDateTime.of(2024, 1, 1, 12, 0))
                .build();
        when(aclService.listRules(isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("cluster-1"), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(rule), 1, 1, 20));

        PageOutput<AclRuleItem> result = handler.execute(new AclListInput(
                "cluster-1", null, null, null, null, null, new PageRequest(1, 20)), context("cluster-1"));

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        AclRuleItem item = result.items().getFirst();
        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.principal()).isEqualTo("user-1");
        assertThat(item.decision()).isEqualTo("GRANT");
        verify(aclService).listRules(isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("cluster-1"), eq(1), eq(20));
    }
}
