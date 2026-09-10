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
package org.apache.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DLQExcelExportResultVOTest {

    @Test
    void builderDefaultsDescribeEmptyExport() {
        DLQExcelExportResultVO vo = DLQExcelExportResultVO.builder().build();

        assertNull(vo.getData());
        assertFalse(vo.isTruncated());
        assertEquals(0, vo.getFailedQueueCount());
        assertEquals(0, vo.getLimit());
    }

    @Test
    void allArgsCarryExportResult() {
        byte[] data = new byte[] {1, 2, 3};

        DLQExcelExportResultVO vo = DLQExcelExportResultVO.builder()
            .data(data)
            .truncated(true)
            .failedQueueCount(1)
            .limit(100)
            .build();

        assertArrayEquals(data, vo.getData());
        assertTrue(vo.isTruncated());
        assertEquals(1, vo.getFailedQueueCount());
        assertEquals(100, vo.getLimit());
    }
}
