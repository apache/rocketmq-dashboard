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
package org.apache.rocketmq.dashboard.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LiteTopicModelTest {

    // ==================== LiteTopicSummary ====================

    @Test
    public void testLiteTopicSummaryAccessors() {
        LiteTopicSummary summary = new LiteTopicSummary();
        Date now = new Date();
        summary.setTopicPattern("lite/%");
        summary.setTopicCount(4);
        summary.setSessionIds(Arrays.asList("s1", "s2"));
        summary.setEarliestCreateTime(now);
        summary.setLastActiveTime(now);
        summary.setAverageTTL(60_000L);
        summary.setMinTTL(30_000L);
        summary.setMaxTTL(120_000L);
        summary.setConsumerCount(8);
        summary.setTotalBacklog(100L);
        summary.setActive(true);
        summary.setNamespace("ns-1");
        summary.setAttributes(Collections.singletonMap("k", "v"));

        assertEquals("lite/%", summary.getTopicPattern());
        assertEquals(Integer.valueOf(4), summary.getTopicCount());
        assertEquals(2, summary.getSessionIds().size());
        assertEquals(now, summary.getEarliestCreateTime());
        assertEquals(now, summary.getLastActiveTime());
        assertEquals(Long.valueOf(60_000L), summary.getAverageTTL());
        assertEquals(Long.valueOf(30_000L), summary.getMinTTL());
        assertEquals(Long.valueOf(120_000L), summary.getMaxTTL());
        assertEquals(Integer.valueOf(8), summary.getConsumerCount());
        assertEquals(Long.valueOf(100L), summary.getTotalBacklog());
        assertTrue(summary.isActive());
        assertEquals("ns-1", summary.getNamespace());
        assertEquals("v", summary.getAttributes().get("k"));
        assertTrue(summary.toString().contains("ns-1"));
    }

    @Test
    public void testLiteTopicSummaryTtlStatus() {
        LiteTopicSummary summary = new LiteTopicSummary();
        assertEquals("UNKNOWN", summary.getTTLStatus());

        summary.setLastActiveTime(new Date());
        summary.setAverageTTL(3_600_000L);
        assertEquals("ACTIVE", summary.getTTLStatus());

        summary.setLastActiveTime(new Date(System.currentTimeMillis() - 10_000));
        summary.setAverageTTL(1_000L);
        assertEquals("EXPIRING_SOON", summary.getTTLStatus());

        // No TTL configured -> treated as ACTIVE
        summary.setAverageTTL(null);
        assertEquals("ACTIVE", summary.getTTLStatus());
    }

    @Test
    public void testLiteTopicSummaryConsumerDensityAndAggregation() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setConsumerCount(10);
        assertEquals(0.0, summary.getConsumerDensity(), 0.0001);
        summary.setTopicCount(0);
        assertEquals(0.0, summary.getConsumerDensity(), 0.0001);
        summary.setTopicCount(5);
        assertEquals(2.0, summary.getConsumerDensity(), 0.0001);

        summary.setConsumerCount(0);
        summary.setTotalBacklog(null);
        assertTrue(summary.isEmptyAggregation());
        summary.setTotalBacklog(0L);
        assertTrue(summary.isEmptyAggregation());
        summary.setTotalBacklog(5L);
        assertFalse(summary.isEmptyAggregation());
        summary.setConsumerCount(3);
        assertFalse(summary.isEmptyAggregation());
    }

    // ==================== LiteTopicSession ====================

    @Test
    public void testLiteTopicSessionAccessors() {
        LiteTopicSession session = new LiteTopicSession();
        Date now = new Date();
        session.setSessionId("session-1");
        session.setClientId("client-1");
        session.setClientAddress("127.0.0.1:1234");
        session.setLiteTopics(new HashSet<>(Arrays.asList("lite/a", "lite/b")));
        session.setParentTopic("lite");
        session.setConsumerGroup("group-1");
        session.setCreateTime(now);
        session.setLastActiveTime(now);
        session.setTtl(60_000L);
        session.setTtlRemaining(30_000L);
        session.setStatus("ACTIVE");
        session.setTotalMessages(200L);
        session.setConsumedMessages(50L);
        session.setPendingMessages(150L);
        session.setConsumptionRate(2.5);
        session.setLiteTopicCreationCount(2);

        assertEquals("session-1", session.getSessionId());
        assertEquals("client-1", session.getClientId());
        assertEquals("127.0.0.1:1234", session.getClientAddress());
        assertEquals(2, session.getLiteTopics().size());
        assertEquals("lite", session.getParentTopic());
        assertEquals("group-1", session.getConsumerGroup());
        assertEquals(now, session.getCreateTime());
        assertEquals(now, session.getLastActiveTime());
        assertEquals(Long.valueOf(60_000L), session.getTtl());
        assertEquals(Long.valueOf(30_000L), session.getTtlRemaining());
        assertEquals("ACTIVE", session.getStatus());
        assertEquals(Long.valueOf(200L), session.getTotalMessages());
        assertEquals(Long.valueOf(50L), session.getConsumedMessages());
        assertEquals(Long.valueOf(150L), session.getPendingMessages());
        assertEquals(Double.valueOf(2.5), session.getConsumptionRate());
        assertEquals(Integer.valueOf(2), session.getLiteTopicCreationCount());
        assertTrue(session.toString().contains("session-1"));
    }

    @Test
    public void testLiteTopicSessionBusinessMethods() {
        LiteTopicSession session = new LiteTopicSession();
        session.setStatus("ACTIVE");
        session.setConsumptionRate(1.5);
        assertTrue(session.hasActiveConsumption());
        session.setConsumptionRate(0.0);
        assertFalse(session.hasActiveConsumption());
        session.setConsumptionRate(null);
        assertFalse(session.hasActiveConsumption());
        session.setStatus("IDLE");
        session.setConsumptionRate(1.5);
        assertFalse(session.hasActiveConsumption());

        session.setStatus("EXPIRED");
        assertTrue(session.isExpired());
        session.setStatus("ACTIVE");
        session.setTtlRemaining(0L);
        assertTrue(session.isExpired());
        session.setTtlRemaining(10L);
        assertFalse(session.isExpired());
        session.setTtlRemaining(null);
        assertFalse(session.isExpired());

        assertEquals(0.0, session.getConsumptionProgress(), 0.0001);
        session.setTotalMessages(0L);
        assertEquals(0.0, session.getConsumptionProgress(), 0.0001);
        session.setTotalMessages(200L);
        session.setConsumedMessages(50L);
        assertEquals(25.0, session.getConsumptionProgress(), 0.0001);
    }

    @Test
    public void testPopConsumeProgress() {
        LiteTopicSession.PopConsumeProgress progress = new LiteTopicSession.PopConsumeProgress();
        progress.setAckTimeoutSeconds(30);
        progress.setMaxReconsumeTimes(16);
        progress.setTotalPopInFlightCount(5);
        progress.setLastPopTime(123456);
        progress.setPopCheckpoint(7);
        progress.setTotalPopCount(100);

        assertEquals(Integer.valueOf(30), progress.getAckTimeoutSeconds());
        assertEquals(Integer.valueOf(16), progress.getMaxReconsumeTimes());
        assertEquals(Integer.valueOf(5), progress.getTotalPopInFlightCount());
        assertEquals(Integer.valueOf(123456), progress.getLastPopTime());
        assertEquals(Integer.valueOf(7), progress.getPopCheckpoint());
        assertEquals(Integer.valueOf(100), progress.getTotalPopCount());

        LiteTopicSession session = new LiteTopicSession();
        session.setPopProgress(progress);
        assertSame(progress, session.getPopProgress());
    }

    // ==================== LiteTopicQuota ====================

    @Test
    public void testLiteTopicQuota() {
        LiteTopicQuota quota = new LiteTopicQuota();
        quota.setCurrentTopicCount(5);
        assertEquals(0.0, quota.getUsageRate(), 0.0001);
        quota.setMaxTopicCount(0);
        assertEquals(0.0, quota.getUsageRate(), 0.0001);
        quota.setMaxTopicCount(10);
        assertEquals(0.5, quota.getUsageRate(), 0.0001);

        quota.setCurrentSessionCount(2);
        assertEquals(0.0, quota.getSessionUsageRate(), 0.0001);
        quota.setMaxSessionCount(0);
        assertEquals(0.0, quota.getSessionUsageRate(), 0.0001);
        quota.setMaxSessionCount(4);
        assertEquals(0.5, quota.getSessionUsageRate(), 0.0001);

        assertTrue(quota.isNearQuotaLimit(0.4));
        assertFalse(quota.isNearQuotaLimit(0.6));

        assertFalse(quota.isQuotaExceeded());
        quota.setCurrentTopicCount(10);
        assertTrue(quota.isQuotaExceeded());

        assertEquals(Integer.valueOf(0), quota.getRemainingQuota());
        quota.setCurrentTopicCount(3);
        assertEquals(Integer.valueOf(7), quota.getRemainingQuota());
        quota.setMaxTopicCount(null);
        assertEquals(Integer.valueOf(0), quota.getRemainingQuota());

        quota.setDefaultTTL(60_000L);
        quota.setMaxTTL(120_000L);
        quota.setCurrentCreationRate(1.0);
        quota.setMaxCreationRate(10.0);
        assertEquals(Long.valueOf(60_000L), quota.getDefaultTTL());
        assertEquals(Long.valueOf(120_000L), quota.getMaxTTL());
        assertEquals(Double.valueOf(1.0), quota.getCurrentCreationRate());
        assertEquals(Double.valueOf(10.0), quota.getMaxCreationRate());
        assertNotNull(quota.toString());
    }

    // ==================== NamespaceInfo ====================

    @Test
    public void testNamespaceInfo() {
        NamespaceInfo info = new NamespaceInfo();
        assertFalse(info.isValid());
        info.setNamespaceName("   ");
        assertFalse(info.isValid());
        info.setNamespaceName("ns-prod");
        assertTrue(info.isValid());

        assertFalse(info.isEnabled());
        info.setStatus("enabled");
        assertTrue(info.isEnabled());
        info.setStatus("DISABLED");
        assertFalse(info.isEnabled());

        Date now = new Date();
        info.setDisplayName("Production");
        info.setDescription("prod namespace");
        info.setClusterName("DefaultCluster");
        info.setCreateTime(now);
        info.setUpdateTime(now);
        info.setAttributes(Collections.singletonMap("env", "prod"));
        info.setDefaultNamespace(true);

        assertEquals("Production", info.getDisplayName());
        assertEquals("prod namespace", info.getDescription());
        assertEquals("DefaultCluster", info.getClusterName());
        assertEquals(now, info.getCreateTime());
        assertEquals(now, info.getUpdateTime());
        assertEquals("prod", info.getAttributes().get("env"));
        assertTrue(info.isDefaultNamespace());
        assertTrue(info.toString().contains("ns-prod"));
    }

    @Test
    public void testNamespaceQuotaConfig() {
        NamespaceInfo.QuotaConfig quota = new NamespaceInfo.QuotaConfig();
        LiteTopicQuota liteQuota = new LiteTopicQuota();
        quota.setMaxTopicCount(100);
        quota.setMaxConsumerGroupCount(50);
        quota.setStorageQuotaGB(1024L);
        quota.setQpsLimit(10000);
        quota.setConnectionLimit(500);
        quota.setLiteTopicQuota(liteQuota);

        assertEquals(Integer.valueOf(100), quota.getMaxTopicCount());
        assertEquals(Integer.valueOf(50), quota.getMaxConsumerGroupCount());
        assertEquals(Long.valueOf(1024L), quota.getStorageQuotaGB());
        assertEquals(Integer.valueOf(10000), quota.getQpsLimit());
        assertEquals(Integer.valueOf(500), quota.getConnectionLimit());
        assertSame(liteQuota, quota.getLiteTopicQuota());

        NamespaceInfo info = new NamespaceInfo();
        info.setQuotaConfig(quota);
        assertSame(quota, info.getQuotaConfig());
    }
}
