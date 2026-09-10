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
package org.apache.rocketmq.studio.ops;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsHomeVOTest {

    @Test
    void builderDefaultsDescribeUnavailableOps() {
        OpsHomeVO vo = OpsHomeVO.builder().build();

        assertFalse(vo.isConfigurationAvailable());
        assertNull(vo.getUnavailableReason());
        assertNull(vo.getNamesvrAddrList());
        assertFalse(vo.isUseVIPChannel());
        assertFalse(vo.isUseTLS());
        assertNull(vo.getCurrentNamesrv());
    }

    @Test
    void allArgsCarryOpsSettings() {
        OpsHomeVO vo = OpsHomeVO.builder()
            .configurationAvailable(true)
            .unavailableReason(null)
            .namesvrAddrList(List.of("10.0.0.1:9876"))
            .useVIPChannel(true)
            .useTLS(false)
            .currentNamesrv("10.0.0.1:9876")
            .build();

        assertTrue(vo.isConfigurationAvailable());
        assertEquals(List.of("10.0.0.1:9876"), vo.getNamesvrAddrList());
        assertTrue(vo.isUseVIPChannel());
        assertFalse(vo.isUseTLS());
        assertEquals("10.0.0.1:9876", vo.getCurrentNamesrv());
    }
}
