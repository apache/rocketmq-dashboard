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
 * Locks the alert silence recurrence vocabulary used by silence schedules.
 */
class AlertSilenceRecurrenceTest {

    @Test
    void exposesAllRecurrenceModes() {
        assertEquals(3, AlertSilenceRecurrence.values().length);
        assertTrue(Arrays.asList(AlertSilenceRecurrence.values()).contains(AlertSilenceRecurrence.ONCE));
        assertTrue(Arrays.asList(AlertSilenceRecurrence.values()).contains(AlertSilenceRecurrence.DAILY));
        assertTrue(Arrays.asList(AlertSilenceRecurrence.values()).contains(AlertSilenceRecurrence.WEEKLY));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("ONCE", AlertSilenceRecurrence.ONCE.name());
        assertEquals("DAILY", AlertSilenceRecurrence.DAILY.name());
        assertEquals("WEEKLY", AlertSilenceRecurrence.WEEKLY.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (AlertSilenceRecurrence recurrence : AlertSilenceRecurrence.values()) {
            assertEquals(recurrence, AlertSilenceRecurrence.valueOf(recurrence.name()));
        }
    }
}
