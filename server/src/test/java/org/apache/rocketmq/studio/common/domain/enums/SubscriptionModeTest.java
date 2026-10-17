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

package org.apache.rocketmq.studio.common.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the vocabulary of {@link SubscriptionMode}, the consumption model a lite topic
 * consumer group runs in: Push (the broker delivers to a resident client) and Pop (the
 * client pulls on demand). The group form and the provider calls both key on these exact
 * names, so a renamed or dropped constant would break the pairing silently.
 */
class SubscriptionModeTest {

    @Test
    void theTwoConsumptionModelsAreStable() {
        Set<String> modes = Arrays.stream(SubscriptionMode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(SubscriptionMode.values()).hasSize(2);
        assertThat(modes).containsExactlyInAnyOrder("Push", "Pop");
    }
}
