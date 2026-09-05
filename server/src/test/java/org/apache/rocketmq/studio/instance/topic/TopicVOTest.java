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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TopicVOTest {

    @Test
    void freshVoCarriesEmptyState() {
        TopicVO vo = new TopicVO();

        assertNull(vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getNamespace());
        assertNull(vo.getType());
        assertEquals(0, vo.getWriteQueues());
        assertEquals(0, vo.getReadQueues());
        assertNull(vo.getPerm());
        assertEquals(0L, vo.getMessageCount());
        assertEquals(0.0, vo.getTps());
        assertEquals(0, vo.getConsumerGroupCount());
    }

    @Test
    void settersRoundTripRepresentativeFields() {
        TopicVO vo = new TopicVO();

        vo.setId(5L);
        vo.setName("orders");
        vo.setClusterId("cluster-1");
        vo.setInstanceId("inst-1");
        vo.setType(TopicType.NORMAL);
        vo.setWriteQueues(8);
        vo.setReadQueues(8);
        vo.setPerm(TopicPerm.RW);
        vo.setMessageCount(1000L);
        vo.setTps(12.5);
        vo.setConsumerGroupCount(3);
        vo.setRemark("order events");

        assertEquals(5L, vo.getId());
        assertEquals("orders", vo.getName());
        assertEquals(TopicType.NORMAL, vo.getType());
        assertEquals(8, vo.getWriteQueues());
        assertEquals(TopicPerm.RW, vo.getPerm());
        assertEquals(1000L, vo.getMessageCount());
        assertEquals(12.5, vo.getTps());
        assertEquals("order events", vo.getRemark());
    }
}
