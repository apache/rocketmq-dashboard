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
package org.apache.rocketmq.studio.cluster.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CollectorStatusVOTest {

    @Test
    void exposesAllRecordComponents() {
        CollectorStatusVO vo = new CollectorStatusVO("30s", 3, 5);

        assertEquals("30s", vo.collectionInterval());
        assertEquals(3, vo.clusterCollectorCount());
        assertEquals(5, vo.businessCollectorCount());
    }

    @Test
    void equalityFollowsRecordComponents() {
        CollectorStatusVO a = new CollectorStatusVO("30s", 3, 5);
        CollectorStatusVO same = new CollectorStatusVO("30s", 3, 5);
        CollectorStatusVO different = new CollectorStatusVO("60s", 3, 5);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
