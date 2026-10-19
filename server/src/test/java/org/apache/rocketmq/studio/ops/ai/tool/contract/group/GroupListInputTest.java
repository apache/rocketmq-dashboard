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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the group list tool input of {@link GroupListInput}: the instance scoping and the
 * optional group-name filter round-trip, and a request without a filter carries null - the
 * tool treats an absent filter as "list every group", not as an empty string.
 */
class GroupListInputTest {

    @Test
    void theInstanceAndFilterRoundTrip() {
        GroupListInput input = new GroupListInput("instance-1", "order");

        assertThat(input.instanceId()).isEqualTo("instance-1");
        assertThat(input.groupName()).isEqualTo("order");
    }

    @Test
    void aRequestWithoutAFilterCarriesNull() {
        GroupListInput input = new GroupListInput("instance-1", null);
        assertThat(input.groupName()).isNull();
    }
}
