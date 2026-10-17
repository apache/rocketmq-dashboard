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
package org.apache.rocketmq.studio.cluster.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the failure report of {@link BrokerConfigUpdateFailureVO}: one broker that could not be
 * updated, its address and the reason - the per-broker detail the diff view shows next to the
 * successful ones. Built with {@code @Value}: two reports of the same address and reason are
 * equal.
 */
class BrokerConfigUpdateFailureVOTest {

    @Test
    void theAddressAndReasonRoundTripThroughTheBuilder() {
        BrokerConfigUpdateFailureVO failure = BrokerConfigUpdateFailureVO.builder()
                .address("10.0.0.1:10911")
                .message("connection refused")
                .build();

        assertThat(failure.getAddress()).isEqualTo("10.0.0.1:10911");
        assertThat(failure.getMessage()).isEqualTo("connection refused");
    }

    @Test
    void equalFailuresAreEqualValues() {
        BrokerConfigUpdateFailureVO first = BrokerConfigUpdateFailureVO.builder()
                .address("10.0.0.1:10911").message("timeout").build();
        BrokerConfigUpdateFailureVO second = BrokerConfigUpdateFailureVO.builder()
                .address("10.0.0.1:10911").message("timeout").build();

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
