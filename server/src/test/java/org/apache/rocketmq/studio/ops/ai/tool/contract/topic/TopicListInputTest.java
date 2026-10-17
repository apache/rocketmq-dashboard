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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the topic list tool input of {@link TopicListInput}: the instance scoping and the two
 * optional filters (name substring, type) round-trip, and a request without filters carries
 * nulls - the tool treats absent filters as "no filtering", not as empty strings.
 */
class TopicListInputTest {

    @Test
    void everyFieldRoundTrips() {
        TopicListInput input = new TopicListInput("instance-1", "order", "NORMAL");

        assertThat(input.instanceId()).isEqualTo("instance-1");
        assertThat(input.topicName()).isEqualTo("order");
        assertThat(input.type()).isEqualTo("NORMAL");
    }

    @Test
    void aRequestWithoutFiltersCarriesNulls() {
        TopicListInput input = new TopicListInput("instance-1", null, null);

        assertThat(input.topicName()).isNull();
        assertThat(input.type()).isNull();
    }
}
