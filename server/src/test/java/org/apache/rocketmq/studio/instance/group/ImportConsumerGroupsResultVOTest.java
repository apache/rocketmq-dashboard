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
package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the consumer group import result of {@link ImportConsumerGroupsResultVO}: the
 * imported/failed counters with the imported groups and the per-entry failures (index, name,
 * message) - the same result shape the topic import renders.
 */
class ImportConsumerGroupsResultVOTest {

    @Test
    void theResultCarriesCountersGroupsAndFailures() {
        ConsumerGroupVO imported = new ConsumerGroupVO();
        imported.setName("order-group");
        ImportConsumerGroupsResultVO.Failure failure = ImportConsumerGroupsResultVO.Failure.builder()
                .index(3)
                .name("payment-group")
                .message("subscription already exists")
                .build();
        ImportConsumerGroupsResultVO result = ImportConsumerGroupsResultVO.builder()
                .imported(2)
                .failed(1)
                .groups(List.of(imported))
                .failures(List.of(failure))
                .build();

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getGroups()).containsExactly(imported);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getIndex()).isEqualTo(3);
        assertThat(result.getFailures().get(0).getName()).isEqualTo("payment-group");
        assertThat(result.getFailures().get(0).getMessage()).isEqualTo("subscription already exists");
    }

    @Test
    void aFreshResultCountsNothing() {
        ImportConsumerGroupsResultVO result = new ImportConsumerGroupsResultVO();
        assertThat(result.getImported()).isZero();
        assertThat(result.getFailed()).isZero();
        assertThat(result.getGroups()).isNull();
        assertThat(result.getFailures()).isNull();
    }
}
