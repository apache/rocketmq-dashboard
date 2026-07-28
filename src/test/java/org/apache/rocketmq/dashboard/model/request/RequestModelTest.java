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
package org.apache.rocketmq.dashboard.model.request;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RequestModelTest {

    @Test
    public void testMetricsDataSourceRequest() {
        MetricsDataSourceRequest request = new MetricsDataSourceRequest();
        assertEquals("PROMETHEUS", request.getProviderType());

        request.setName("prom");
        request.setType("PROMETHEUS");
        request.setProviderType("VICTORIAMETRICS");
        request.setUrl("http://prometheus:9090");
        request.setUsername("user");
        request.setPassword("pass");
        request.setBearerToken("token");
        request.setDefault(true);
        request.setReadOnly(true);
        request.setCustomHeaders("X-Scope-OrgID: tenant-1");
        request.setConnectionTimeoutMs(5000);
        request.setReadTimeoutMs(30000);
        request.setDescription("main data source");

        assertEquals("prom", request.getName());
        assertEquals("PROMETHEUS", request.getType());
        assertEquals("VICTORIAMETRICS", request.getProviderType());
        assertEquals("http://prometheus:9090", request.getUrl());
        assertEquals("user", request.getUsername());
        assertEquals("pass", request.getPassword());
        assertEquals("token", request.getBearerToken());
        assertTrue(request.isDefault());
        assertTrue(request.isReadOnly());
        assertEquals("X-Scope-OrgID: tenant-1", request.getCustomHeaders());
        assertEquals(Integer.valueOf(5000), request.getConnectionTimeoutMs());
        assertEquals(Integer.valueOf(30000), request.getReadTimeoutMs());
        assertEquals("main data source", request.getDescription());
        assertTrue(request.toString().contains("prom"));
        assertNotEquals(request, new MetricsDataSourceRequest());
        assertEquals(request, request);
    }

    @Test
    public void testArchitectureSwitchRequest() {
        ArchitectureSwitchRequest request = new ArchitectureSwitchRequest();
        request.setAccessType("V5_PROXY_CLUSTER");
        request.setProxyAddresses(new String[] {"proxy1:8080", "proxy2:8080"});
        request.setNameSrvAddress("127.0.0.1:9876");
        request.setNamespace("ns-1");

        assertEquals("V5_PROXY_CLUSTER", request.getAccessType());
        assertArrayEquals(new String[] {"proxy1:8080", "proxy2:8080"}, request.getProxyAddresses());
        assertEquals("127.0.0.1:9876", request.getNameSrvAddress());
        assertEquals("ns-1", request.getNamespace());
    }

    @Test
    public void testTopicTypeMeta() {
        TopicTypeMeta meta = new TopicTypeMeta();
        meta.setTopicName("topicA");
        meta.setMessageType("FIFO");
        assertEquals("topicA", meta.getTopicName());
        assertEquals("FIFO", meta.getMessageType());

        TopicTypeMeta empty = new TopicTypeMeta();
        assertFalse("topicA".equals(empty.getTopicName()));
    }
}
