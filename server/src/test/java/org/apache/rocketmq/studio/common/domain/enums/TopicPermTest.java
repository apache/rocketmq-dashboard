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
 * Locks the topic permission vocabulary shared by the ACL rule payloads.
 */
class TopicPermTest {

    @Test
    void exposesAllTopicPermissions() {
        assertEquals(3, TopicPerm.values().length);
        assertTrue(Arrays.asList(TopicPerm.values()).contains(TopicPerm.RW));
        assertTrue(Arrays.asList(TopicPerm.values()).contains(TopicPerm.RO));
        assertTrue(Arrays.asList(TopicPerm.values()).contains(TopicPerm.WO));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("RW", TopicPerm.RW.name());
        assertEquals("RO", TopicPerm.RO.name());
        assertEquals("WO", TopicPerm.WO.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (TopicPerm perm : TopicPerm.values()) {
            assertEquals(perm, TopicPerm.valueOf(perm.name()));
        }
    }
}
