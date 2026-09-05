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
 * Locks the topic type vocabulary used by topic create/edit forms and the frontend selector.
 */
class TopicTypeTest {

    @Test
    void exposesAllTopicTypes() {
        assertEquals(5, TopicType.values().length);
        assertTrue(Arrays.asList(TopicType.values()).contains(TopicType.NORMAL));
        assertTrue(Arrays.asList(TopicType.values()).contains(TopicType.FIFO));
        assertTrue(Arrays.asList(TopicType.values()).contains(TopicType.DELAY));
        assertTrue(Arrays.asList(TopicType.values()).contains(TopicType.TRANSACTION));
        assertTrue(Arrays.asList(TopicType.values()).contains(TopicType.LITE));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("NORMAL", TopicType.NORMAL.name());
        assertEquals("TRANSACTION", TopicType.TRANSACTION.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (TopicType type : TopicType.values()) {
            assertEquals(type, TopicType.valueOf(type.name()));
        }
    }
}
