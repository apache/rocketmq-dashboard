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

import org.apache.rocketmq.studio.instance.topic.LiteTopicService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicExtendTtlInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicExtendTtlOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LiteTopicExtendTtlToolHandlerTest {

    @Mock
    private LiteTopicService liteTopicService;

    @InjectMocks
    private LiteTopicExtendTtlToolHandler handler;

    @Test
    void exposesTheCatalogedToolNameTest() {
        assertThat(handler.name()).isEqualTo("rmq.litetopic.extend_ttl");
        assertThat(handler.inputType()).isEqualTo(LiteTopicExtendTtlInput.class);
    }

    @Test
    void previewIsDerivedFromTheRequestWithoutTouchingTheBrokerTest() {
        ToolPlan plan = handler.preview(new LiteTopicExtendTtlInput("orders", 86_400_000L), context());

        verifyNoInteractions(liteTopicService);
        assertThat(plan.summary()).isEqualTo("extend the LiteTopic TTL under parent topic 'orders' in instance 'instance-a'.");
        assertThat(plan.after()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "topicPattern", "orders",
                "newTtlMillis", 86_400_000L));
        // A whole-minute value inside the cap carries no clamp warnings.
        assertThat(plan.warnings()).isEmpty();
        assertThat(plan.impact()).anyMatch(text -> text.contains("lite.topic.expiration"));
    }

    @Test
    void previewWarnsAboutMinuteGranularityAndTheThirtyDayCapTest() {
        ToolPlan unaligned = handler.preview(new LiteTopicExtendTtlInput("orders", 90_000L), context());
        assertThat(unaligned.warnings()).anyMatch(text -> text.contains("one minute"));

        ToolPlan overCap = handler.preview(new LiteTopicExtendTtlInput(
                "orders", TimeUnit.DAYS.toMillis(31)), context());
        assertThat(overCap.warnings()).anyMatch(text -> text.contains("30 days"));

        ToolPlan exactCap = handler.preview(new LiteTopicExtendTtlInput(
                "orders", TimeUnit.DAYS.toMillis(30)), context());
        assertThat(exactCap.warnings()).isEmpty();
    }

    @Test
    void executeDelegatesToTheOwnershipGuardedServiceTest() {
        LiteTopicExtendTtlOutput output = handler.execute(
                new LiteTopicExtendTtlInput("orders", 86_400_000L), context());

        verify(liteTopicService).extendTTL("instance-a", "orders", 86_400_000L);
        assertThat(output).isEqualTo(new LiteTopicExtendTtlOutput("orders", 86_400_000L));
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of("instance-a", null, Map.of("instanceId", "instance-a"));
    }
}
