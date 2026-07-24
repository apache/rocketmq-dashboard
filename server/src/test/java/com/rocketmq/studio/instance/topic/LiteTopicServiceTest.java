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

package com.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiteTopicServiceTest {

    private final LiteTopicService liteTopicService = new LiteTopicService();

    @Test
    void listLiteTopicsShouldFilterByPatternAndNamespace() {
        List<LiteTopicItemVO> result = liteTopicService.listLiteTopics("chat", "default");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTopicPattern()).isEqualTo("chat/{sessionId}");
        assertThat(result.get(0).getNamespace()).isEqualTo("default");
    }

    @Test
    void getQuotaShouldReturnDefaultLimits() {
        LiteTopicQuotaVO quota = liteTopicService.getQuota("default");

        assertThat(quota.getCurrentTopicCount()).isEqualTo(128);
        assertThat(quota.getMaxTopicCount()).isEqualTo(1_000_000);
        assertThat(quota.getRemainingQuota()).isEqualTo(999_872);
    }

    @Test
    void getSessionShouldReturnRequestedSessionId() {
        LiteTopicSessionVO session = liteTopicService.getSession("sess-001");

        assertThat(session.getSessionId()).isEqualTo("sess-001");
        assertThat(session.getLiteTopics()).extracting("topicName").contains("chat/sess-001");
    }

    @Test
    void extendTTLShouldRejectInvalidInput() {
        assertThatThrownBy(() -> liteTopicService.extendTTL("", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topicPattern is required");
        assertThatThrownBy(() -> liteTopicService.extendTTL("chat/{sessionId}", 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("newTTL must be positive");
    }
}
