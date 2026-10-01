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
package org.apache.rocketmq.studio.ops.ai.tool.handler.litetopic;

import org.apache.rocketmq.studio.instance.topic.LiteTopicQuotaVO;
import org.apache.rocketmq.studio.instance.topic.LiteTopicService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicQuotaInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicQuotaOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiteTopicQuotaToolHandlerTest {

    @Mock
    private LiteTopicService liteTopicService;

    @InjectMocks
    private LiteTopicQuotaToolHandler handler;

    @Test
    void mapsTheFullWatermarkTest() {
        when(liteTopicService.getQuota("ns-a")).thenReturn(LiteTopicQuotaVO.builder()
                .currentTopicCount(120)
                .maxTopicCount(1000)
                .currentSessionCount(30)
                .maxSessionCount(100)
                .currentCreationRate(5)
                .maxCreationRate(50)
                .usageRate(0.12)
                .sessionUsageRate(0.3)
                .defaultTTL(3600L)
                .maxTTL(86400L)
                .remainingQuota(880)
                .consumerDensity(2.5)
                .build());

        LiteTopicQuotaOutput output = handler.execute(new LiteTopicQuotaInput("ns-a"), context());

        verify(liteTopicService).getQuota("ns-a");
        assertThat(output).isEqualTo(new LiteTopicQuotaOutput(
                120, 1000, 30, 100, 5, 50, 0.12, 0.3, 3600L, 86400L, 880, 2.5));
    }

    @Test
    void keepsPartialWatermarksPartialTest() {
        when(liteTopicService.getQuota(null)).thenReturn(LiteTopicQuotaVO.builder()
                .currentTopicCount(7)
                .build());

        LiteTopicQuotaOutput output = handler.execute(new LiteTopicQuotaInput(null), context());

        assertThat(output.currentTopicCount()).isEqualTo(7);
        assertThat(output.maxTopicCount()).isNull();
        assertThat(output.usageRate()).isNull();
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of(null, null, Map.of());
    }
}
