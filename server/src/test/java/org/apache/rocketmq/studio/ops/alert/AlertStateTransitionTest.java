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
 * Locks the state machine transition vocabulary used by the alert state machine.
 */
class AlertStateTransitionTest {

    @Test
    void exposesAllTransitions() {
        assertEquals(5, AlertStateTransition.values().length);
        assertTrue(Arrays.asList(AlertStateTransition.values()).contains(AlertStateTransition.NONE));
        assertTrue(Arrays.asList(AlertStateTransition.values()).contains(AlertStateTransition.PENDING));
        assertTrue(Arrays.asList(AlertStateTransition.values()).contains(AlertStateTransition.FIRING));
        assertTrue(Arrays.asList(AlertStateTransition.values()).contains(AlertStateTransition.REMINDER));
        assertTrue(Arrays.asList(AlertStateTransition.values()).contains(AlertStateTransition.RESOLVED));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("PENDING", AlertStateTransition.PENDING.name());
        assertEquals("REMINDER", AlertStateTransition.REMINDER.name());
        assertEquals("RESOLVED", AlertStateTransition.RESOLVED.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (AlertStateTransition transition : AlertStateTransition.values()) {
            assertEquals(transition, AlertStateTransition.valueOf(transition.name()));
        }
    }
}
