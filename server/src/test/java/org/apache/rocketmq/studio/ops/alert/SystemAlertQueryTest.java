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

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemAlertQueryTest {

    @Test
    void exposesAllRecordComponents() {
        LocalDateTime from = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-09-02T00:00:00");
        SystemAlertQuery query = new SystemAlertQuery("critical", AlertDomain.CLUSTER, "inst-1",
                "FIRING", "cluster", "DefaultCluster", from, to, 2, 20, true);

        assertEquals("critical", query.level());
        assertEquals(AlertDomain.CLUSTER, query.domain());
        assertEquals("inst-1", query.instanceId());
        assertEquals("FIRING", query.transition());
        assertEquals("cluster", query.labelKey());
        assertEquals("DefaultCluster", query.labelValue());
        assertEquals(from, query.from());
        assertEquals(to, query.to());
        assertEquals(2, query.page());
        assertEquals(20, query.pageSize());
        assertEquals(Boolean.TRUE, query.notificationSuppressed());
    }

    @Test
    void convenienceConstructorDefaultsNotificationSuppressionToNull() {
        SystemAlertQuery query = new SystemAlertQuery("warning", AlertDomain.BUSINESS, null,
                null, null, null, null, null, 1, 20);

        assertNull(query.notificationSuppressed());
        assertNull(query.instanceId());
    }

    @Test
    void equalityFollowsRecordComponents() {
        SystemAlertQuery a = new SystemAlertQuery("warning", AlertDomain.BUSINESS, null,
                null, null, null, null, null, 1, 20);
        SystemAlertQuery same = new SystemAlertQuery("warning", AlertDomain.BUSINESS, null,
                null, null, null, null, null, 1, 20);
        SystemAlertQuery different = new SystemAlertQuery("critical", AlertDomain.BUSINESS, null,
                null, null, null, null, null, 1, 20);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
