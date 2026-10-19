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
package org.apache.rocketmq.studio.ops.ai.tool.contract.acl;

import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AclMutationInput}: the tool-side ACL rule shape. The id translation is the subtle
 * half - the tool world speaks string ids (they arrive from the model and the catalog), the
 * storage world speaks a Long, and the mapping must be exact in both directions.
 */
class AclMutationInputTest {

    @Test
    void toRuleMapsEveryFieldAndTakesTheNumericIdFromTheCaller() {
        AclMutationInput input = new AclMutationInput(
                "instance-a", "17", "User:alice", "Topic:orders", "Topic", "LITERAL",
                List.of("PUB", "SUB"), "ALLOW", "DEFAULT");

        AclRuleVO rule = input.toRule(17L);

        assertThat(rule.getId()).isEqualTo(17L);
        assertThat(rule.getPrincipal()).isEqualTo("User:alice");
        assertThat(rule.getResource()).isEqualTo("Topic:orders");
        assertThat(rule.getResourceType()).isEqualTo("Topic");
        assertThat(rule.getResourcePattern()).isEqualTo("LITERAL");
        assertThat(rule.getActions()).containsExactly("PUB", "SUB");
        assertThat(rule.getDecision()).isEqualTo("ALLOW");
        assertThat(rule.getScope()).isEqualTo("DEFAULT");
    }

    @Test
    void fromTranslatesTheNumericIdIntoTheToolWorldString() {
        AclRuleVO stored = AclRuleVO.builder()
                .id(42L).principal("User:bob").resource("Group:workers")
                .resourceType("Group").resourcePattern("PREFIXED")
                .actions(List.of("SUB")).decision("DENY").scope("CUSTOM")
                .build();

        AclMutationInput input = AclMutationInput.from(stored);

        assertThat(input.id()).isEqualTo("42");
        assertThat(input.principal()).isEqualTo("User:bob");
        assertThat(input.resource()).isEqualTo("Group:workers");
        assertThat(input.resourceType()).isEqualTo("Group");
        assertThat(input.resourcePattern()).isEqualTo("PREFIXED");
        assertThat(input.actions()).containsExactly("SUB");
        assertThat(input.decision()).isEqualTo("DENY");
        assertThat(input.scope()).isEqualTo("CUSTOM");
    }

    @Test
    void anUnsavedRuleHasNoIdInEitherWorld() {
        AclRuleVO unsaved = AclRuleVO.builder().principal("User:carol").build();

        AclMutationInput input = AclMutationInput.from(unsaved);

        assertThat(input.id()).isNull();
    }

    @Test
    void theRoundTripPreservesEveryMutationField() {
        AclMutationInput original = new AclMutationInput(
                null, "7", "User:dave", "Topic:audit", "Topic", "LITERAL",
                List.of("PUB"), "ALLOW", "DEFAULT");

        AclMutationInput roundTripped = AclMutationInput.from(original.toRule(7L));

        assertThat(roundTripped).isEqualTo(original);
    }
}
