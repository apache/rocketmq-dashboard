/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPlanTest {

    record TopicState(String topic, Integer queues, String remark) {
    }

    @Test
    void builderPrunesNullValuedStateEntries() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("topic", "orders");
        state.put("remark", null);

        ToolPlan plan = ToolPlan.builder("update topic").after(state).build();

        assertThat(plan.after()).containsKey("topic").doesNotContainKey("remark");
    }

    @Test
    void builderNullStateBecomesEmptyMap() {
        ToolPlan plan = ToolPlan.builder("update topic").before(null).build();

        assertThat(plan.before()).isEmpty();
    }

    @Test
    void stateMapsAreDeeplyImmutable() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", "value");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("nested", nested);

        ToolPlan plan = ToolPlan.builder("update topic").after(state).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) plan.after().get("nested");
        assertThatThrownBy(() -> view.put("inner", "mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.after().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorSnapshotsCallerOwnedListsAndMaps() {
        List<String> impact = new ArrayList<>(List.of("restarts brokers"));
        List<String> warnings = new ArrayList<>(List.of("first warning"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("topic", "orders");

        ToolPlan plan = new ToolPlan("summary", impact, Map.of(), after, warnings);
        impact.add("mutated impact");
        warnings.add("mutated warning");
        after.put("topic", "mutated");

        assertThat(plan.impact()).containsExactly("restarts brokers");
        assertThat(plan.warnings()).containsExactly("first warning");
        assertThat(plan.after()).containsEntry("topic", "orders");
    }

    @Test
    void withWarningReturnsANewPlanAndLeavesTheOriginalUntouched() {
        ToolPlan original = ToolPlan.builder("update topic").warning("first").build();

        ToolPlan updated = original.withWarning("second");

        assertThat(original.warnings()).containsExactly("first");
        assertThat(updated.warnings()).containsExactly("first", "second");
        assertThat(updated.summary()).isEqualTo(original.summary());
    }

    @Test
    void warningIfAppliesTheWarningOnlyWhenTheConditionHolds() {
        ToolPlan conditional = ToolPlan.builder("update topic").warningIf(true, "applied").build();
        ToolPlan skipped = ToolPlan.builder("update topic").warningIf(false, "applied").build();

        assertThat(conditional.warnings()).containsExactly("applied");
        assertThat(skipped.warnings()).isEmpty();
    }

    @Test
    void typedStateReadsRoundTripThroughTheBuilder() {
        ToolPlan plan = ToolPlan.builder("update topic")
                .before(new TopicState("orders", 8, null))
                .after(new TopicState("orders", 16, "scaled"))
                .build();

        assertThat(plan.before(TopicState.class)).isEqualTo(new TopicState("orders", 8, null));
        assertThat(plan.after(TopicState.class)).isEqualTo(new TopicState("orders", 16, "scaled"));
    }

    @Test
    void typedStateReadsIgnoreUnknownJsonKeys() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("topic", "orders");
        raw.put("queues", 8);
        raw.put("futureField", "unknown to the record");

        ToolPlan plan = ToolPlan.builder("update topic").after(raw).build();

        assertThat(plan.after(TopicState.class)).isEqualTo(new TopicState("orders", 8, null));
    }
}
