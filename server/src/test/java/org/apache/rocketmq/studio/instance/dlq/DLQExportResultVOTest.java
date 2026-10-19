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
 * Pins the DLQ export result of {@link DLQExportResultVO}: the exported rows travel with the
 * three scan-completeness signals (truncated, failedQueueCount, limit) - the same X-DLQ-Export-*
 * contract the response headers expose.
 */
class DLQExportResultVOTest {

    @Test
    void theResultCarriesItsCompletenessSignals() {
        DLQMessageVO message = DLQMessageVO.builder().msgId("msg-1").build();
        DLQExportResultVO result = DLQExportResultVO.builder()
                .messages(List.of(message))
                .truncated(true)
                .failedQueueCount(2)
                .limit(5000)
                .build();

        assertThat(result.getMessages()).containsExactly(message);
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getFailedQueueCount()).isEqualTo(2);
        assertThat(result.getLimit()).isEqualTo(5000);
    }

    @Test
    void aFreshExportReadsAsCompleteAndUnlimited() {
        DLQExportResultVO result = new DLQExportResultVO();
        assertThat(result.getMessages()).isNull();
        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getFailedQueueCount()).isZero();
        assertThat(result.getLimit()).isZero();
    }
}
