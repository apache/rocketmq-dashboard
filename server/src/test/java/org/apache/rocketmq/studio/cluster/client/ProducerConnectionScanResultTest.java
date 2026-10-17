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
package org.apache.rocketmq.studio.cluster.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ProducerConnectionScanResult}: producer connection rows plus the coverage gaps.
 * The compact constructor null-safes and defensively copies every list, and complete() is the
 * signal that the scan saw every broker and every producer group it tried.
 */
class ProducerConnectionScanResultTest {

    @Test
    void aCompleteScanHasNoGaps() {
        ProducerConnectionScanResult result = ProducerConnectionScanResult.complete(List.of());

        assertThat(result.complete()).isTrue();
        assertThat(result.failedBrokers()).isEmpty();
        assertThat(result.failedProducerGroups()).isEmpty();
    }

    @Test
    void anyGapMakesTheScanIncomplete() {
        ProducerConnectionScanResult result = new ProducerConnectionScanResult(
                List.of(), List.of("broker-a"), List.of());

        assertThat(result.complete()).isFalse();
    }

    @Test
    void nullListsNormaliseToEmpty() {
        ProducerConnectionScanResult result = new ProducerConnectionScanResult(null, null, null);

        assertThat(result.connections()).isNotNull().isEmpty();
        assertThat(result.failedBrokers()).isNotNull().isEmpty();
    }

    @Test
    void theListsAreDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(List.of("broker-a"));
        ProducerConnectionScanResult result = new ProducerConnectionScanResult(null, mutable, null);

        mutable.add("broker-b");

        assertThat(result.failedBrokers()).containsExactly("broker-a");
    }
}
