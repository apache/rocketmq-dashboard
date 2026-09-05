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
package org.apache.rocketmq.studio.cluster.nameserver;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NameserverRegistryVOTest {

    @Test
    void builderDefaultsDescribeEmptyRegistryEntry() {
        NameserverRegistryVO vo = NameserverRegistryVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getNamesrvAddr());
        assertNull(vo.getK8sNamespace());
        assertNull(vo.getStatus());
        assertNull(vo.getGmtCreate());
    }

    @Test
    void allArgsCarryRegistryEntry() {
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        NameserverRegistryVO vo = NameserverRegistryVO.builder()
            .id(2L)
            .name("ns-1")
            .namesrvAddr("10.132.218.11:9876")
            .k8sNamespace("rocketmq")
            .k8sId("nameserver-0")
            .status("RUNNING")
            .description("primary")
            .gmtCreate(created)
            .gmtModified(created)
            .build();

        assertEquals(2L, vo.getId());
        assertEquals("ns-1", vo.getName());
        assertEquals("10.132.218.11:9876", vo.getNamesrvAddr());
        assertEquals("rocketmq", vo.getK8sNamespace());
        assertEquals("RUNNING", vo.getStatus());
        assertEquals(created, vo.getGmtCreate());
    }
}
