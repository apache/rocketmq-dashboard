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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertRuleQueryTest {

    @Test
    void exposesAllRecordComponents() {
        AlertRuleQuery query = new AlertRuleQuery(AlertDomain.CLUSTER, "disk", true, 2, 20);

        assertEquals(AlertDomain.CLUSTER, query.domain());
        assertEquals("disk", query.search());
        assertEquals(Boolean.TRUE, query.enabled());
        assertEquals(2, query.page());
        assertEquals(20, query.pageSize());
    }

    @Test
    void optionalFiltersMayBeNull() {
        AlertRuleQuery query = new AlertRuleQuery(null, null, null, 1, 20);

        assertNull(query.domain());
        assertNull(query.search());
        assertNull(query.enabled());
    }

    @Test
    void equalityFollowsRecordComponents() {
        AlertRuleQuery a = new AlertRuleQuery(AlertDomain.BUSINESS, null, null, 1, 20);
        AlertRuleQuery same = new AlertRuleQuery(AlertDomain.BUSINESS, null, null, 1, 20);
        AlertRuleQuery different = new AlertRuleQuery(AlertDomain.CLUSTER, null, null, 1, 20);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
