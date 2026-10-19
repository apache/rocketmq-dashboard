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
package org.apache.rocketmq.studio.ops.ai.tool.contract.plan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link PlanDescription}: the impact and warning lists are defensively copied, and
 * {@code builder()} formats the summary with the caller's arguments and seeds the plan builder
 * with every impact and warning.
 */
class PlanDescriptionTest {

    @Test
    void theListsAreDefensivelyCopied() {
        List<String> impact = new ArrayList<>(List.of("restarts brokers"));
        List<String> warnings = new ArrayList<>(List.of("cannot be undone"));
        PlanDescription description = new PlanDescription("summarize", impact, warnings);

        impact.add("mutated afterwards");
        warnings.add("mutated afterwards");

        assertThat(description.impact()).containsExactly("restarts brokers");
        assertThat(description.warnings()).containsExactly("cannot be undone");
    }

    @Test
    void theBuilderFormatsTheSummaryWithTheArguments() {
        PlanDescription description = new PlanDescription(
                "reset offsets of group %s to %d",
                List.of("stops the group"),
                List.of("message loss"));

        ToolPlan.Builder builder = description.builder("order-group", 42);

        ToolPlan plan = builder.build();
        assertThat(plan.summary()).isEqualTo("reset offsets of group order-group to 42");
    }

    @Test
    void theBuilderSeedsEveryImpactAndWarning() {
        PlanDescription description = new PlanDescription(
                "summary",
                List.of("impact one", "impact two"),
                List.of("warning one"));

        ToolPlan plan = description.builder().build();

        assertThat(plan.impact()).containsExactly("impact one", "impact two");
        assertThat(plan.warnings()).containsExactly("warning one");
    }
}
