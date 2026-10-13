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

package org.apache.rocketmq.studio.provider;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the vocabulary of {@link InstanceCapability}: the capability names every provider
 * resolution and the capabilities endpoint speak. The frontend gates its actions on these
 * exact names, so a renamed or dropped constant would silently disable a working action
 * instead of failing loudly.
 */
class InstanceCapabilityTest {

    @Test
    void theCapabilityVocabularyIsStable() {
        Set<String> capabilities = Arrays.stream(InstanceCapability.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(InstanceCapability.values()).hasSize(8);
        assertThat(capabilities).containsExactlyInAnyOrder(
                "TOPIC_MANAGEMENT",
                "CONSUMER_GROUP_MANAGEMENT",
                "MESSAGE_QUERY",
                "MESSAGE_TRACE",
                "MESSAGE_SEND",
                "DIRECT_MESSAGE_CONSUME",
                "ACL_MANAGEMENT",
                "DLQ_MANAGEMENT");
    }
}
