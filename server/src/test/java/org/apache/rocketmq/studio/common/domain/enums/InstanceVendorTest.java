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
package org.apache.rocketmq.studio.common.domain.enums;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the instance vendor vocabulary used by cloud import payloads and catalog filters.
 */
class InstanceVendorTest {

    @Test
    void exposesAllInstanceVendors() {
        assertEquals(3, InstanceVendor.values().length);
        assertTrue(Arrays.asList(InstanceVendor.values()).contains(InstanceVendor.APACHE));
        assertTrue(Arrays.asList(InstanceVendor.values()).contains(InstanceVendor.ALIYUN));
        assertTrue(Arrays.asList(InstanceVendor.values()).contains(InstanceVendor.TENCENT));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("APACHE", InstanceVendor.APACHE.name());
        assertEquals("ALIYUN", InstanceVendor.ALIYUN.name());
        assertEquals("TENCENT", InstanceVendor.TENCENT.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (InstanceVendor vendor : InstanceVendor.values()) {
            assertEquals(vendor, InstanceVendor.valueOf(vendor.name()));
        }
    }
}
