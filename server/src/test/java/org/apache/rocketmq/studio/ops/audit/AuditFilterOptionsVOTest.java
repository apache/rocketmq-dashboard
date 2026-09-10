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
package org.apache.rocketmq.studio.ops.audit;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditFilterOptionsVOTest {

    @Test
    void builderDefaultsDescribeEmptyOptions() {
        AuditFilterOptionsVO vo = AuditFilterOptionsVO.builder().build();

        assertNull(vo.getOperationTypes());
        assertNull(vo.getResourceTypes());
        assertNull(vo.getClusterIds());
        assertNull(vo.getResults());
    }

    @Test
    void allArgsCarryFilterOptions() {
        AuditFilterOptionsVO vo = AuditFilterOptionsVO.builder()
            .operationTypes(List.of("CREATE_TOPIC", "DELETE_TOPIC"))
            .resourceTypes(List.of("TOPIC", "GROUP"))
            .clusterIds(List.of("cluster-1"))
            .results(List.of("SUCCESS", "FAILED"))
            .build();

        assertEquals(List.of("CREATE_TOPIC", "DELETE_TOPIC"), vo.getOperationTypes());
        assertEquals(List.of("TOPIC", "GROUP"), vo.getResourceTypes());
        assertEquals(List.of("cluster-1"), vo.getClusterIds());
        assertEquals(List.of("SUCCESS", "FAILED"), vo.getResults());
    }
}
