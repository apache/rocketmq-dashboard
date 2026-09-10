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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TopicConsumerPageVOTest {

    @Test
    void builderDefaultsDescribeEmptyPage() {
        TopicConsumerPageVO vo = TopicConsumerPageVO.builder().build();

        assertNull(vo.getItems());
        assertEquals(0, vo.getTotal());
        assertEquals(0, vo.getPage());
        assertEquals(0, vo.getPageSize());
    }

    @Test
    void allArgsCarryPageState() {
        TopicConsumerVO item = TopicConsumerVO.builder().group("cg-orders").build();

        TopicConsumerPageVO vo = TopicConsumerPageVO.builder()
            .items(List.of(item))
            .total(1)
            .page(1)
            .pageSize(20)
            .build();

        assertEquals(List.of(item), vo.getItems());
        assertEquals(1, vo.getTotal());
        assertEquals(1, vo.getPage());
        assertEquals(20, vo.getPageSize());
    }
}
