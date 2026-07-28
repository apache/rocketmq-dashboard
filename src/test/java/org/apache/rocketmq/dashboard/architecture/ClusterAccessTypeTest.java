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
package org.apache.rocketmq.dashboard.architecture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ClusterAccessType}.
 */
public class ClusterAccessTypeTest {

    @Test
    public void testGetValueAndDescription() {
        assertEquals("v4-namesrv", ClusterAccessType.V4_NAMESRV.getValue());
        assertEquals("4.0 Direct Connection", ClusterAccessType.V4_NAMESRV.getDescription());
        assertEquals("v5-proxy-local", ClusterAccessType.V5_PROXY_LOCAL.getValue());
        assertEquals("5.0 Proxy Local Mode", ClusterAccessType.V5_PROXY_LOCAL.getDescription());
        assertEquals("v5-proxy-cluster", ClusterAccessType.V5_PROXY_CLUSTER.getValue());
        assertEquals("cloud-aliyun", ClusterAccessType.CLOUD_ALIYUN.getValue());
        assertEquals("cloud-tencent", ClusterAccessType.CLOUD_TENCENT.getValue());
        assertEquals("cloud-huawei", ClusterAccessType.CLOUD_HUAWEI.getValue());
    }

    @Test
    public void testIsCloudProvider() {
        assertTrue(ClusterAccessType.CLOUD_ALIYUN.isCloudProvider());
        assertTrue(ClusterAccessType.CLOUD_TENCENT.isCloudProvider());
        assertTrue(ClusterAccessType.CLOUD_HUAWEI.isCloudProvider());
        assertFalse(ClusterAccessType.V4_NAMESRV.isCloudProvider());
        assertFalse(ClusterAccessType.V5_PROXY_LOCAL.isCloudProvider());
    }

    @Test
    public void testIsV5Architecture() {
        assertTrue(ClusterAccessType.V5_PROXY_LOCAL.isV5Architecture());
        assertTrue(ClusterAccessType.V5_PROXY_CLUSTER.isV5Architecture());
        assertFalse(ClusterAccessType.V4_NAMESRV.isV5Architecture());
        assertFalse(ClusterAccessType.CLOUD_ALIYUN.isV5Architecture());
    }

    @Test
    public void testIsV4Architecture() {
        assertTrue(ClusterAccessType.V4_NAMESRV.isV4Architecture());
        assertFalse(ClusterAccessType.V5_PROXY_LOCAL.isV4Architecture());
        assertFalse(ClusterAccessType.V5_PROXY_CLUSTER.isV4Architecture());
        assertFalse(ClusterAccessType.CLOUD_HUAWEI.isV4Architecture());
    }

    @Test
    public void testFromValue() {
        for (ClusterAccessType type : ClusterAccessType.values()) {
            assertEquals(type, ClusterAccessType.fromValue(type.getValue()));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromValueUnknownThrows() {
        ClusterAccessType.fromValue("unknown-type");
    }
}
