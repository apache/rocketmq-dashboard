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

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceCapabilitiesVOTest {

    @Test
    void exposesAllRecordComponents() {
        InstanceCapabilitiesVO vo = new InstanceCapabilitiesVO(
                "inst-1", InstanceVendor.APACHE, InstanceType.DIRECT, List.of());

        assertEquals("inst-1", vo.instanceId());
        assertEquals(InstanceVendor.APACHE, vo.vendor());
        assertEquals(InstanceType.DIRECT, vo.accessType());
        assertTrue(vo.capabilities().isEmpty());
    }

    @Test
    void equalityFollowsRecordComponents() {
        InstanceCapabilitiesVO a = new InstanceCapabilitiesVO(
                "inst-1", InstanceVendor.APACHE, InstanceType.DIRECT, List.of());
        InstanceCapabilitiesVO same = new InstanceCapabilitiesVO(
                "inst-1", InstanceVendor.APACHE, InstanceType.DIRECT, List.of());
        InstanceCapabilitiesVO different = new InstanceCapabilitiesVO(
                "inst-2", InstanceVendor.APACHE, InstanceType.DIRECT, List.of());

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
