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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAlertRepositoryTest {

    private final InMemoryAlertRepository repository = new InMemoryAlertRepository();

    @Test
    void replaceRuleShouldUpdateExistingRule() {
        AlertRuleVO existing = AlertRuleVO.builder()
                .id("rule-1")
                .name("Original rule")
                .build();
        AlertRuleVO replacement = AlertRuleVO.builder()
                .id("rule-1")
                .name("Updated rule")
                .build();
        repository.saveRule(existing);

        boolean replaced = repository.replaceRule(replacement);

        assertThat(replaced).isTrue();
        assertThat(repository.findAllRules()).containsExactly(replacement);
    }

    @Test
    void replaceRuleShouldNotInsertUnknownRule() {
        AlertRuleVO replacement = AlertRuleVO.builder()
                .id("missing")
                .name("Missing rule")
                .build();

        boolean replaced = repository.replaceRule(replacement);

        assertThat(replaced).isFalse();
        assertThat(repository.findAllRules()).isEmpty();
    }
}
