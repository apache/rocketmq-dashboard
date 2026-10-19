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
 * Pins the vocabulary of {@link TopicType}, the kind of a topic: the ordinary kind, the FIFO
 * order-preserving kind, the delayed kind, the transactional kind and the serverless-lite
 * kind. The topic list badges and the create form both key on these exact names, so a
 * renamed or dropped constant would silently un-type every topic.
 */
class TopicTypeTest {

    @Test
    void theFiveTopicKindsAreStable() {
        Set<String> kinds = Arrays.stream(TopicType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(TopicType.values()).hasSize(5);
        assertThat(kinds).containsExactlyInAnyOrder("NORMAL", "FIFO", "DELAY", "TRANSACTION", "LITE");
    }
}
