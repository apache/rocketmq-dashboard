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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClusterInfoService}.
 *
 * <p>{@code init()} is only invoked in the dedicated scheduler test (and the
 * scheduler is shut down afterwards) to avoid background refresh interference.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ClusterInfoServiceTest {

    @InjectMocks
    private ClusterInfoService clusterInfoService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(clusterInfoService, "cacheExpireMs", 60000L);
    }

    @After
    public void tearDown() {
        ScheduledExecutorService scheduler =
            (ScheduledExecutorService) ReflectionTestUtils.getField(clusterInfoService, "scheduler");
        scheduler.shutdownNow();
    }

    @Test
    public void testGetRefreshesWhenCacheEmpty() throws Exception {
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        assertSame(clusterInfo, clusterInfoService.get());
        // Second call is served from cache without another remote call
        assertSame(clusterInfo, clusterInfoService.get());
        verify(mqAdminExt, times(1)).examineBrokerClusterInfo();
    }

    @Test
    public void testRefreshUpdatesCache() throws Exception {
        ClusterInfo first = MockObjectUtil.createClusterInfo();
        ClusterInfo second = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(first, second);

        assertSame(first, clusterInfoService.refresh());
        assertSame(second, clusterInfoService.refresh());
        assertSame(second, clusterInfoService.get());
    }

    @Test
    public void testRefreshFailureReturnsStaleCache() throws Exception {
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo())
            .thenReturn(clusterInfo)
            .thenThrow(new RuntimeException("namesrv down"));

        assertSame(clusterInfo, clusterInfoService.refresh());
        // Failure falls back to the previously cached value
        assertSame(clusterInfo, clusterInfoService.refresh());
    }

    @Test
    public void testRefreshFailureWithoutCacheThrows() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenThrow(new RuntimeException("namesrv down"));

        try {
            clusterInfoService.refresh();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertNotNull(e.getCause());
        }
    }

    @Test
    public void testInitSchedulesPeriodicRefresh() throws Exception {
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        clusterInfoService.init();
        // The initial run (delay 0) populates the cache shortly after scheduling
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(mqAdminExt).examineBrokerClusterInfo();
                break;
            } catch (AssertionError retry) {
                Thread.sleep(50);
            }
        }
        assertSame(clusterInfo, clusterInfoService.get());
    }
}
