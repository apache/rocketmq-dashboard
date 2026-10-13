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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bulk retry result of {@link NotificationDeliveryBulkRetryResult}: the succeeded ids
 * and the per-id failure reasons travel together - the response shape the bulk retry dialog
 * renders row by row. As an @Value type two results of the same content are equal.
 */
class NotificationDeliveryBulkRetryResultTest {

    @Test
    void theResultCarriesSuccessesAndFailures() {
        NotificationDeliveryBulkRetryResult result = new NotificationDeliveryBulkRetryResult(
                List.of(7L, 8L), Map.of(9L, "outbox row locked"));

        assertThat(result.getSucceededIds()).containsExactly(7L, 8L);
        assertThat(result.getFailures()).containsEntry(9L, "outbox row locked");
    }

    @Test
    void equalResultsAreEqualValues() {
        NotificationDeliveryBulkRetryResult first = new NotificationDeliveryBulkRetryResult(
                List.of(7L), Map.of());
        NotificationDeliveryBulkRetryResult second = new NotificationDeliveryBulkRetryResult(
                List.of(7L), Map.of());
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
