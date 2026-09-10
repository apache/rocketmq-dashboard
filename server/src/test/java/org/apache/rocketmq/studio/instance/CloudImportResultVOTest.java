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
package org.apache.rocketmq.studio.instance;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudImportResultVOTest {

    @Test
    void builderDefaultsDescribeEmptyImport() {
        CloudImportResultVO vo = CloudImportResultVO.builder().build();

        assertEquals(0, vo.getDiscovered());
        assertEquals(0, vo.getImported());
        assertEquals(0, vo.getSkipped());
        assertEquals(0, vo.getFailedCount());
        assertFalse(vo.isFailureDetailsTruncated());
        assertNull(vo.getFailed());
    }

    @Test
    void allArgsCarryImportSummary() {
        CloudImportResultVO vo = CloudImportResultVO.builder()
            .discovered(5)
            .imported(4)
            .skipped(1)
            .failedCount(2)
            .failureDetailsTruncated(true)
            .failed(List.of("inst-x"))
            .build();

        assertEquals(5, vo.getDiscovered());
        assertEquals(4, vo.getImported());
        assertEquals(1, vo.getSkipped());
        assertEquals(2, vo.getFailedCount());
        assertTrue(vo.isFailureDetailsTruncated());
        assertEquals(List.of("inst-x"), vo.getFailed());
    }
}
