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
package org.apache.rocketmq.studio.instance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the cloud import outcome of {@link CloudImportResultVO}: the discovery/import/skip
 * counters, the bounded failure list and its honesty flag - when the flag is set the list
 * holds only the first portion of failures, so the UI must say "showing the first N" instead
 * of implying completeness.
 */
class CloudImportResultVOTest {

    @Test
    void theCountersRoundTripThroughTheBuilder() {
        CloudImportResultVO result = CloudImportResultVO.builder()
                .discovered(10)
                .imported(6)
                .skipped(2)
                .failedCount(2)
                .failureDetailsTruncated(false)
                .failed(java.util.List.of("rmq-1: already imported", "rmq-2: credential rejected"))
                .build();

        assertThat(result.getDiscovered()).isEqualTo(10);
        assertThat(result.getImported()).isEqualTo(6);
        assertThat(result.getSkipped()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.isFailureDetailsTruncated()).isFalse();
        assertThat(result.getFailed()).hasSize(2);
    }

    @Test
    void aTruncatedFailureListCarriesMoreThanItShows() {
        CloudImportResultVO result = CloudImportResultVO.builder()
                .discovered(100)
                .imported(50)
                .skipped(0)
                .failedCount(50)
                .failureDetailsTruncated(true)
                .failed(java.util.List.of("rmq-1: timeout"))
                .build();

        // the honesty flag: the list is a bounded portion, not the whole set
        assertThat(result.getFailedCount()).isEqualTo(50);
        assertThat(result.getFailed()).hasSize(1);
        assertThat(result.isFailureDetailsTruncated()).isTrue();
    }

    @Test
    void aFreshResultCountsZeroOfEverything() {
        CloudImportResultVO result = new CloudImportResultVO();
        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getImported()).isZero();
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.isFailureDetailsTruncated()).isFalse();
    }
}
