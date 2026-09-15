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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class AclUpdateToolHandlerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private AclUpdateToolHandler handler;

    @Test
    void applyShouldUpdateRule() {
        assertThat(handler.name()).isEqualTo("rmq.acl.update");
        AclRuleVO updated = AclRuleVO.builder()
                .id(1L)
                .principal("user-1")
                .resource("TopicB")
                .decision("DENY")
                .build();
        when(aclService.updateRule(any(AclRuleVO.class), eq("cluster-1")))
                .thenReturn(updated);

        Object result = handler.execute(new AclMutationInput(
                "cluster-1", "1", "user-1", "TopicB", null, null,
                null, "DENY", null, null), context("cluster-1"));

        assertThat(result).isInstanceOf(AclRuleVO.class);
        AclRuleVO rule = (AclRuleVO) result;
        assertThat(rule.getResource()).isEqualTo("TopicB");

        ArgumentCaptor<AclRuleVO> captor = ArgumentCaptor.forClass(AclRuleVO.class);
        verify(aclService).updateRule(captor.capture(), eq("cluster-1"));
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

}
