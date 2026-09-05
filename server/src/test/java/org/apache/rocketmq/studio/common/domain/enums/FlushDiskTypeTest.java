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
package org.apache.rocketmq.studio.common.domain.enums;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the flush-disk strategy vocabulary shared by broker config payloads.
 */
class FlushDiskTypeTest {

    @Test
    void exposesBothFlushStrategies() {
        assertEquals(2, FlushDiskType.values().length);
        assertTrue(Arrays.asList(FlushDiskType.values()).contains(FlushDiskType.ASYNC_FLUSH));
        assertTrue(Arrays.asList(FlushDiskType.values()).contains(FlushDiskType.SYNC_FLUSH));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("ASYNC_FLUSH", FlushDiskType.ASYNC_FLUSH.name());
        assertEquals("SYNC_FLUSH", FlushDiskType.SYNC_FLUSH.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (FlushDiskType type : FlushDiskType.values()) {
            assertEquals(type, FlushDiskType.valueOf(type.name()));
        }
    }
}
