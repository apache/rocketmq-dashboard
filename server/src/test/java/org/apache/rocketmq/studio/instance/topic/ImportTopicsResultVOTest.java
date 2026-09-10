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

class ImportTopicsResultVOTest {

    @Test
    void builderDefaultsDescribeEmptyImport() {
        ImportTopicsResultVO vo = ImportTopicsResultVO.builder().build();

        assertEquals(0, vo.getImported());
        assertEquals(0, vo.getFailed());
        assertNull(vo.getTopics());
        assertNull(vo.getFailures());
    }

    @Test
    void allArgsCarryImportResult() {
        ImportTopicsResultVO.Failure failure = ImportTopicsResultVO.Failure.builder()
            .index(1)
            .name("orders")
            .message("invalid queues")
            .build();

        ImportTopicsResultVO vo = ImportTopicsResultVO.builder()
            .imported(2)
            .failed(1)
            .topics(List.of())
            .failures(List.of(failure))
            .build();

        assertEquals(2, vo.getImported());
        assertEquals(1, vo.getFailed());
        assertEquals(List.of(), vo.getTopics());
        assertEquals(1, vo.getFailures().get(0).getIndex());
        assertEquals("orders", vo.getFailures().get(0).getName());
    }
}
