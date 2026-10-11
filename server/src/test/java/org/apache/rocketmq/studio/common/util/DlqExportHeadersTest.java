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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the DLQ export header names of {@link DlqExportHeaders}: the web UI reads these response
 * headers to warn when an export is truncated or some queues could not be scanned, so the names
 * must stay exactly these - and {@code ALL} must list every one of them, because the CORS
 * mapping exposes exactly that list to cross-origin JavaScript.
 */
class DlqExportHeadersTest {

    @Test
    void theHeaderNamesAreExactlyThese() {
        assertThat(DlqExportHeaders.TRUNCATED).isEqualTo("X-DLQ-Export-Truncated");
        assertThat(DlqExportHeaders.FAILED_QUEUES).isEqualTo("X-DLQ-Export-FailedQueues");
        assertThat(DlqExportHeaders.LIMIT).isEqualTo("X-DLQ-Export-Limit");
    }

    @Test
    void allListsEveryHeader() {
        assertThat(DlqExportHeaders.ALL).containsExactlyInAnyOrder(
                "X-DLQ-Export-Truncated",
                "X-DLQ-Export-FailedQueues",
                "X-DLQ-Export-Limit");
    }
}
