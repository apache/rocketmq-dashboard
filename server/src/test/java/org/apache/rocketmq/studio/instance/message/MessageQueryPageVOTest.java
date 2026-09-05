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
package org.apache.rocketmq.studio.instance.message;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageQueryPageVOTest {

    @Test
    void builderDefaultsDescribeEmptyPage() {
        MessageQueryPageVO vo = MessageQueryPageVO.builder().build();

        assertNull(vo.getItems());
        assertEquals(0L, vo.getTotal());
        assertEquals(0, vo.getPage());
        assertEquals(0, vo.getSize());
        assertFalse(vo.isResultMayBeTruncated());
    }

    @Test
    void allArgsCarryPageState() {
        MessageQueryPageVO vo = MessageQueryPageVO.builder()
            .items(List.of())
            .total(200L)
            .page(2)
            .size(50)
            .resultMayBeTruncated(true)
            .build();

        assertTrue(vo.getItems().isEmpty());
        assertEquals(200L, vo.getTotal());
        assertEquals(2, vo.getPage());
        assertEquals(50, vo.getSize());
        assertTrue(vo.isResultMayBeTruncated());
    }
}
