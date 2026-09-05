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
 * Locks the alert domain vocabulary that scopes business vs cluster alert rules.
 */
class AlertDomainTest {

    @Test
    void exposesBothAlertDomains() {
        assertEquals(2, AlertDomain.values().length);
        assertTrue(Arrays.asList(AlertDomain.values()).contains(AlertDomain.BUSINESS));
        assertTrue(Arrays.asList(AlertDomain.values()).contains(AlertDomain.CLUSTER));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("BUSINESS", AlertDomain.BUSINESS.name());
        assertEquals("CLUSTER", AlertDomain.CLUSTER.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (AlertDomain domain : AlertDomain.values()) {
            assertEquals(domain, AlertDomain.valueOf(domain.name()));
        }
    }
}
