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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPermissionTest {

    @Test
    void parsesResourceAndAction() {
        ToolPermission permission = ToolPermission.parse("TOPIC:read");

        assertEquals("topic", permission.resource());
        assertEquals("read", permission.action());
    }

    @Test
    void normalizesToLowerCaseAndTrims() {
        ToolPermission permission = ToolPermission.parse("  Topic :  READ  ");

        assertEquals("topic", permission.resource());
        assertEquals("read", permission.action());
    }

    @Test
    void actionDefaultsToEmptyWhenMissing() {
        ToolPermission permission = ToolPermission.parse("topic");

        assertEquals("topic", permission.resource());
        assertEquals("", permission.action());
    }

    @Test
    void toleratesExtraColonsInValue() {
        ToolPermission permission = ToolPermission.parse("topic:write:extra");

        assertEquals("topic", permission.resource());
        assertEquals("write:extra", permission.action());
    }

    @Test
    void blankInputYieldsEmptyPermission() {
        ToolPermission permission = ToolPermission.parse("   ");

        assertEquals("", permission.resource());
        assertEquals("", permission.action());
        assertFalse(permission.isReadOnly());
    }

    @Test
    void nullInputYieldsEmptyPermission() {
        ToolPermission permission = ToolPermission.parse(null);

        assertEquals("", permission.resource());
        assertEquals("", permission.action());
    }

    @Test
    void readActionIsReadOnly() {
        assertTrue(ToolPermission.parse("topic:read").isReadOnly());
        assertFalse(ToolPermission.parse("topic:write").isReadOnly());
        assertFalse(ToolPermission.parse("topic").isReadOnly());
    }
}
