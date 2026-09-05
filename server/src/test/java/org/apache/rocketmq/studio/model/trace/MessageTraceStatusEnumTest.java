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
package org.apache.rocketmq.studio.model.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the trace status vocabulary and its wire string mapping used by the message
 * trace graph views.
 */
class MessageTraceStatusEnumTest {

    @Test
    void exposesAllTraceStatuses() {
        assertEquals(3, MessageTraceStatusEnum.values().length);
    }

    @Test
    void successMapsToLowercaseWireString() {
        assertEquals("success", MessageTraceStatusEnum.SUCCESS.getStatus());
    }

    @Test
    void failedMapsToLowercaseWireString() {
        assertEquals("failed", MessageTraceStatusEnum.FAILED.getStatus());
    }

    @Test
    void unknownMapsToLowercaseWireString() {
        assertEquals("unknown", MessageTraceStatusEnum.UNKNOWN.getStatus());
    }

    @Test
    void statusStringsAreDistinctFromEnumNames() {
        assertEquals("SUCCESS", MessageTraceStatusEnum.SUCCESS.name());
        assertEquals("success", MessageTraceStatusEnum.SUCCESS.getStatus());
    }
}
