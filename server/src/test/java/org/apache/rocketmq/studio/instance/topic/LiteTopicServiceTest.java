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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiteTopicServiceTest {

    private final LiteTopicService liteTopicService = new LiteTopicService();

    @Test
    void listLiteTopicsShouldNotReturnSampleData() {
        List<LiteTopicItemVO> result = liteTopicService.listLiteTopics("hat", " DEFAULT ");

        assertThat(result).isEmpty();
    }

    @Test
    void getQuotaShouldReturnUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> liteTopicService.getQuota("default"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo("LiteTopic provider integration is not available");
                });
    }

    @Test
    void getSessionShouldReturnUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> liteTopicService.getSession("sess-001"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo("LiteTopic provider integration is not available");
                });
    }

    @Test
    void extendTTLShouldRejectInvalidInput() {
        assertThatThrownBy(() -> liteTopicService.extendTTL("", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topicPattern is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> liteTopicService.extendTTL("chat/{sessionId}", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("newTTL must be positive")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void extendTTLShouldReturnUnsupportedWhenProviderIsUnavailable() {
        assertThatThrownBy(() -> liteTopicService.extendTTL("chat/{sessionId}", 7_200_000L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(501);
                    assertThat(ex.getMessage()).isEqualTo("LiteTopic provider integration is not available");
                });
    }

    @Test
    void getCapabilityShouldReportUnsupportedByDefault() {
        assertThat(liteTopicService.getCapability().isSupported()).isFalse();
    }
}
