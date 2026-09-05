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
package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubscriptionEntryVOTest {

    @Test
    void builderDefaultsDescribeEmptyEntry() {
        SubscriptionEntryVO vo = SubscriptionEntryVO.builder().build();

        assertNull(vo.getTopic());
        assertNull(vo.getExpression());
        assertNull(vo.getType());
        assertNull(vo.getFilterMode());
        assertNull(vo.getConsistency());
    }

    @Test
    void allArgsCarrySubscriptionState() {
        SubscriptionEntryVO vo = SubscriptionEntryVO.builder()
            .topic("orders")
            .expression("*")
            .type("TAG")
            .filterMode("SQL92")
            .consistency("LOCAL")
            .build();

        assertEquals("orders", vo.getTopic());
        assertEquals("*", vo.getExpression());
        assertEquals("TAG", vo.getType());
        assertEquals("SQL92", vo.getFilterMode());
        assertEquals("LOCAL", vo.getConsistency());
    }
}
