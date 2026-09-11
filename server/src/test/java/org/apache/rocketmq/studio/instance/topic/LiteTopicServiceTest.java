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

package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.LiteTopicQuota;
import org.apache.rocketmq.studio.model.LiteTopicSession;
import org.apache.rocketmq.studio.model.LiteTopicSummary;
import org.apache.rocketmq.studio.provider.LiteTopicProvider;
import org.apache.rocketmq.studio.provider.apache.RocketMQLiteTopicProvider;
import org.apache.rocketmq.studio.provider.apache.RocketMQProperties;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteTopicServiceTest {

    /** No NameServer configured: the provider reports the feature as unavailable. */
    private final LiteTopicService unavailableService = new LiteTopicService(
            new RocketMQLiteTopicProvider(new MqAdminExtFactory(), new RocketMQProperties()));

    @Test
    void listLiteTopicsShouldReportUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> unavailableService.listLiteTopics("hat", " DEFAULT "))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo(LiteTopicProvider.UNSUPPORTED);
                });
    }

    @Test
    void getQuotaShouldReportUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> unavailableService.getQuota("default"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo(LiteTopicProvider.UNSUPPORTED);
                });
    }

    @Test
    void getSessionShouldReportUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> unavailableService.getSession("parent~group~client"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo(LiteTopicProvider.UNSUPPORTED);
                });
    }

    @Test
    void extendTTLShouldRejectInvalidInput() {
        assertThatThrownBy(() -> unavailableService.extendTTL("", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topicPattern is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> unavailableService.extendTTL("chat/{sessionId}", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("newTTL must be positive")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void extendTTLShouldReportUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> unavailableService.extendTTL("chat/{sessionId}", 7_200_000L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo(LiteTopicProvider.UNSUPPORTED);
                });
    }

    @Test
    void getSessionShouldRejectBlankSessionId() {
        assertThatThrownBy(() -> unavailableService.getSession("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("sessionId is required");
    }

    @Test
    void getCapabilityShouldReportUnsupportedByDefault() {
        assertThat(unavailableService.getCapability().isSupported()).isFalse();
    }

    @Test
    void listLiteTopicsShouldMapProviderModelsOntoViewObjects() {
        LiteTopicProvider provider = mock(LiteTopicProvider.class);
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setTopicPattern("chat");
        summary.setNamespace("DEFAULT");
        summary.setTopicCount(3);
        summary.setConsumerCount(2);
        summary.setTotalBacklog(41L);
        summary.setAverageTTL(1_800_000L);
        summary.setLastActiveTime(new Date(1_700_000_000_000L));
        summary.setSessionIds(List.of("chat~g~c1"));
        when(provider.listLiteTopics("chat", "DEFAULT")).thenReturn(List.of(summary));
        when(provider.isSupported()).thenReturn(true);

        LiteTopicService service = new LiteTopicService(provider);
        List<LiteTopicItemVO> items = service.listLiteTopics("chat", "DEFAULT");

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getTopicPattern()).isEqualTo("chat");
            assertThat(item.getNamespace()).isEqualTo("DEFAULT");
            assertThat(item.getTopicCount()).isEqualTo(3);
            assertThat(item.getConsumerCount()).isEqualTo(2);
            assertThat(item.getTotalBacklog()).isEqualTo(41L);
            assertThat(item.getAverageTTL()).isEqualTo(1_800_000L);
            assertThat(item.getLastActiveTime()).isEqualTo(1_700_000_000_000L);
            assertThat(item.getSessionIds()).containsExactly("chat~g~c1");
            // lastActiveTime is in the past relative to a 30-minute TTL, so the session is expired.
            assertThat(item.getTtlStatus()).isEqualTo("EXPIRED");
        });
        assertThat(service.getCapability().isSupported()).isTrue();
    }

    @Test
    void getSessionShouldMapProviderModelOntoViewObject() {
        LiteTopicProvider provider = mock(LiteTopicProvider.class);
        LiteTopicSession session = new LiteTopicSession();
        session.setSessionId("chat~g~c1");
        session.setClientId("c1");
        session.setClientAddress("10.0.0.9:1234");
        session.setParentTopic("chat");
        session.setConsumerGroup("g");
        session.setTtl(1_800_000L);
        session.setTtlRemaining(900_000L);
        session.setStatus("ACTIVE");
        session.setTotalMessages(120L);
        session.setConsumedMessages(100L);
        session.setPendingMessages(20L);
        session.setLiteTopicCreationCount(2);
        session.setLiteTopics(new LinkedHashSet<>(List.of("bob", "alice")));
        when(provider.getSession("chat~g~c1")).thenReturn(session);

        LiteTopicSessionVO vo = new LiteTopicService(provider).getSession("chat~g~c1");

        assertThat(vo.getSessionId()).isEqualTo("chat~g~c1");
        assertThat(vo.getClientAddress()).isEqualTo("10.0.0.9:1234");
        assertThat(vo.getTtl()).isEqualTo(1_800_000L);
        assertThat(vo.getTtlRemaining()).isEqualTo(900_000L);
        assertThat(vo.getTotalMessages()).isEqualTo(120L);
        assertThat(vo.getConsumedMessages()).isEqualTo(100L);
        assertThat(vo.getPendingMessages()).isEqualTo(20L);
        assertThat(vo.getLiteTopicCreationCount()).isEqualTo(2);
        // Every lite topic inherits the parent topic's TTL policy.
        assertThat(vo.getLiteTopics()).extracting(LiteTopicSessionVO.SessionLiteTopic::getTopicName)
                .containsExactly("bob", "alice");
        assertThat(vo.getLiteTopics()).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo("ACTIVE");
            assertThat(row.getTtlRemaining()).isEqualTo(900_000L);
        });
    }

    @Test
    void getQuotaShouldMapProviderModelOntoViewObject() {
        LiteTopicProvider provider = mock(LiteTopicProvider.class);
        LiteTopicQuota quota = new LiteTopicQuota();
        quota.setCurrentTopicCount(10);
        quota.setMaxTopicCount(40);
        quota.setCurrentSessionCount(4);
        quota.setMaxSessionCount(100_000);
        quota.setDefaultTTL(900_000L);
        quota.setMaxTTL(2_592_000_000L);
        quota.setCurrentCreationRate(0.0);
        quota.setMaxCreationRate(0.0);
        when(provider.getQuota("DEFAULT")).thenReturn(quota);

        LiteTopicQuotaVO vo = new LiteTopicService(provider).getQuota("DEFAULT");

        assertThat(vo.getCurrentTopicCount()).isEqualTo(10);
        assertThat(vo.getMaxTopicCount()).isEqualTo(40);
        assertThat(vo.getUsageRate()).isEqualTo(0.25);
        assertThat(vo.getSessionUsageRate()).isCloseTo(0.00004, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(vo.getRemainingQuota()).isEqualTo(30);
        assertThat(vo.getDefaultTTL()).isEqualTo(900_000L);
        assertThat(vo.getMaxTTL()).isEqualTo(2_592_000_000L);
        assertThat(vo.getConsumerDensity()).isEqualTo(0.4);
        assertThat(vo.getCurrentCreationRate()).isZero();
    }
}
