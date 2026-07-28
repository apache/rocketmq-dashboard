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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LiteTopicServiceImplTest {

    @InjectMocks
    private LiteTopicServiceImpl liteTopicService;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private MetadataProvider metadataProvider;

    @Before
    public void setUp() throws Exception {
        // Make sure parent class fields are populated even if @InjectMocks misses them
        setParentField("clusterProvider", clusterProvider);
        setParentField("metadataProvider", metadataProvider);
    }

    private void setParentField(String name, Object value) throws Exception {
        Field field = ArchitectureBasedService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(liteTopicService, value);
    }

    private void givenLiteTopicSupported(boolean supported) throws Exception {
        ClusterCapability capability = new ClusterCapability();
        capability.setLiteTopicSupported(supported);
        capability.setArchitectureVersion(supported ? "5.0" : "4.0");
        when(clusterProvider.getClusterCapability()).thenReturn(capability);
    }

    @Test
    public void testListLiteTopicsNotSupportedReturnsEmpty() throws Exception {
        givenLiteTopicSupported(false);
        List<LiteTopicSummary> result = liteTopicService.listLiteTopics("pattern*", Optional.empty());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testListLiteTopicsSupported() throws Exception {
        givenLiteTopicSupported(true);
        LiteTopicSummary summary = new LiteTopicSummary();
        when(metadataProvider.listLiteTopics(anyString(), any()))
            .thenReturn(Collections.singletonList(summary));

        List<LiteTopicSummary> result = liteTopicService.listLiteTopics("lite%", Optional.of("ns"));
        assertEquals(1, result.size());
        assertSame(summary, result.get(0));
    }

    @Test
    public void testListLiteTopicsProviderUnsupportedReturnsEmpty() throws Exception {
        givenLiteTopicSupported(true);
        when(metadataProvider.listLiteTopics(any(), any()))
            .thenThrow(new UnsupportedOperationException("not supported"));

        List<LiteTopicSummary> result = liteTopicService.listLiteTopics(null, Optional.empty());
        assertTrue(result.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetLiteTopicSessionNotSupported() throws Exception {
        givenLiteTopicSupported(false);
        liteTopicService.getLiteTopicSession("session-1");
    }

    @Test
    public void testGetLiteTopicSessionSupported() throws Exception {
        givenLiteTopicSupported(true);
        LiteTopicSession session = new LiteTopicSession();
        when(metadataProvider.getLiteTopicSession("session-1")).thenReturn(session);

        assertSame(session, liteTopicService.getLiteTopicSession("session-1"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetLiteTopicSessionProviderUnsupported() throws Exception {
        givenLiteTopicSupported(true);
        when(metadataProvider.getLiteTopicSession(anyString()))
            .thenThrow(new UnsupportedOperationException("no session rpc"));
        liteTopicService.getLiteTopicSession("session-1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExtendLiteTopicTTLNotSupported() throws Exception {
        givenLiteTopicSupported(false);
        liteTopicService.extendLiteTopicTTL("lite-topic", 1000L);
    }

    @Test
    public void testExtendLiteTopicTTLEmptyPattern() throws Exception {
        givenLiteTopicSupported(true);
        try {
            liteTopicService.extendLiteTopicTTL("  ", 1000L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Topic pattern"));
        }
    }

    @Test
    public void testExtendLiteTopicTTLNonPositiveTTL() throws Exception {
        givenLiteTopicSupported(true);
        try {
            liteTopicService.extendLiteTopicTTL("lite-topic", 0L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("TTL"));
        }
    }

    @Test
    public void testExtendLiteTopicTTLSuccess() throws Exception {
        givenLiteTopicSupported(true);
        liteTopicService.extendLiteTopicTTL("lite-topic", 60000L);
        verify(metadataProvider).extendLiteTopicTTL("lite-topic", 60000L);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExtendLiteTopicTTLProviderUnsupported() throws Exception {
        givenLiteTopicSupported(true);
        doThrow(new UnsupportedOperationException("ttl not supported"))
            .when(metadataProvider).extendLiteTopicTTL(anyString(), anyLong());
        liteTopicService.extendLiteTopicTTL("lite-topic", 60000L);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetLiteTopicQuotaNotSupported() throws Exception {
        givenLiteTopicSupported(false);
        liteTopicService.getLiteTopicQuota(Optional.empty());
    }

    @Test
    public void testGetLiteTopicQuotaSupported() throws Exception {
        givenLiteTopicSupported(true);
        LiteTopicQuota quota = new LiteTopicQuota();
        when(metadataProvider.getLiteTopicQuota(any())).thenReturn(quota);

        assertSame(quota, liteTopicService.getLiteTopicQuota(Optional.of("ns")));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetLiteTopicQuotaProviderUnsupported() throws Exception {
        givenLiteTopicSupported(true);
        when(metadataProvider.getLiteTopicQuota(any()))
            .thenThrow(new UnsupportedOperationException("quota not supported"));
        liteTopicService.getLiteTopicQuota(Optional.empty());
    }

    @Test
    public void testIsLiteTopicSupportedTrue() throws Exception {
        givenLiteTopicSupported(true);
        assertTrue(liteTopicService.isLiteTopicSupported());
    }

    @Test
    public void testIsLiteTopicSupportedFalse() throws Exception {
        givenLiteTopicSupported(false);
        assertFalse(liteTopicService.isLiteTopicSupported());
    }

    @Test
    public void testIsLiteTopicSupportedWhenCapabilityUnavailable() throws Exception {
        when(clusterProvider.getClusterCapability()).thenThrow(new RuntimeException("cluster down"));
        assertFalse(liteTopicService.isLiteTopicSupported());
    }

    @Test
    public void testListLiteTopicsSupportedReturnsProviderResultNotNull() throws Exception {
        givenLiteTopicSupported(true);
        when(metadataProvider.listLiteTopics(any(), any())).thenReturn(Collections.emptyList());
        assertNotNull(liteTopicService.listLiteTopics(null, Optional.empty()));
    }
}
