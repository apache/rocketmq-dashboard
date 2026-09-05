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

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the notification outbox lifecycle vocabulary used by the delivery flows.
 */
class NotificationOutboxStatusTest {

    @Test
    void exposesAllOutboxLifecycleStates() {
        assertEquals(5, NotificationOutboxStatus.values().length);
        assertTrue(Arrays.asList(NotificationOutboxStatus.values())
                .contains(NotificationOutboxStatus.PENDING));
        assertTrue(Arrays.asList(NotificationOutboxStatus.values())
                .contains(NotificationOutboxStatus.SENDING));
        assertTrue(Arrays.asList(NotificationOutboxStatus.values())
                .contains(NotificationOutboxStatus.DELIVERED));
        assertTrue(Arrays.asList(NotificationOutboxStatus.values())
                .contains(NotificationOutboxStatus.RETRY_WAIT));
        assertTrue(Arrays.asList(NotificationOutboxStatus.values())
                .contains(NotificationOutboxStatus.FAILED));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("PENDING", NotificationOutboxStatus.PENDING.name());
        assertEquals("RETRY_WAIT", NotificationOutboxStatus.RETRY_WAIT.name());
        assertEquals("FAILED", NotificationOutboxStatus.FAILED.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (NotificationOutboxStatus status : NotificationOutboxStatus.values()) {
            assertEquals(status, NotificationOutboxStatus.valueOf(status.name()));
        }
    }
}
