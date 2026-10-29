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
package org.apache.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the DLQ resend result of {@link DLQResendResultVO}: the matched/resent/failed counters,
 * the outcome verdict, the scan-completeness flag with its failed-queue count, and the bounded
 * failure list with its own truncation flag - the resend dialog's whole answer.
 */
class DLQResendResultVOTest {

    @Test
    void aCompleteResendCarriesItsCountersAndVerdict() {
        DLQResendResultVO result = DLQResendResultVO.builder()
                .matched(40)
                .resent(40)
                .failed(0)
                .outcome("SUCCESS")
                .scanIncomplete(false)
                .failedQueueCount(0)
                .build();

        assertThat(result.getMatched()).isEqualTo(40);
        assertThat(result.getResent()).isEqualTo(40);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getOutcome()).isEqualTo("SUCCESS");
        assertThat(result.isScanIncomplete()).isFalse();
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    void anIncompleteScanReportsItsFailedQueues() {
        DLQResendFailureVO failure = DLQResendFailureVO.builder().build();
        DLQResendResultVO result = DLQResendResultVO.builder()
                .matched(20)
                .resent(20)
                .failed(0)
                .outcome("PARTIAL")
                .scanIncomplete(true)
                .failedQueueCount(2)
                .failures(List.of(failure))
                .failuresTruncated(false)
                .build();

        assertThat(result.isScanIncomplete()).isTrue();
        assertThat(result.getFailedQueueCount()).isEqualTo(2);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.isFailuresTruncated()).isFalse();
    }

    @Test
    void aTruncatedFailureListSaysSo() {
        DLQResendResultVO result = DLQResendResultVO.builder()
                .matched(100)
                .resent(50)
                .failed(50)
                .outcome("PARTIAL")
                .failuresTruncated(true)
                .build();

        // the list was bounded by the provider; the flag is the honesty marker
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.isFailuresTruncated()).isTrue();
    }
}
