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
package org.apache.rocketmq.studio.settings;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataSourceVOTest {

    @Test
    void builderDefaultsDescribeEmptySource() {
        DataSourceVO vo = DataSourceVO.builder().build();

        assertNull(vo.getKey());
        assertNull(vo.getName());
        assertNull(vo.getType());
        assertNull(vo.getUrl());
        assertNull(vo.getAuth());
        assertNull(vo.getStatus());
        assertNull(vo.getInstanceIds());
    }

    @Test
    void allArgsCarryDataSourceState() {
        DataSourceVO vo = DataSourceVO.builder()
            .key("prometheus-1")
            .name("Prometheus")
            .type("PROMETHEUS")
            .url("http://prometheus:9090")
            .auth("none")
            .status("UP")
            .instanceIds(List.of("inst-1"))
            .build();

        assertEquals("prometheus-1", vo.getKey());
        assertEquals("Prometheus", vo.getName());
        assertEquals("PROMETHEUS", vo.getType());
        assertEquals("http://prometheus:9090", vo.getUrl());
        assertEquals("none", vo.getAuth());
        assertEquals("UP", vo.getStatus());
        assertEquals(List.of("inst-1"), vo.getInstanceIds());
    }
}
