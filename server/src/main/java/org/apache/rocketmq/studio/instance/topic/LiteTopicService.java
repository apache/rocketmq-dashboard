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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiteTopicService {
    private static final String PROVIDER_UNAVAILABLE_MESSAGE = "LiteTopic provider integration is not available";
    private static final int NOT_IMPLEMENTED = 501;

    public List<LiteTopicItemVO> listLiteTopics(String pattern, String namespace) {
        return List.of();
    }

    public LiteTopicSessionVO getSession(String sessionId) {
        throw new BusinessException(NOT_IMPLEMENTED, PROVIDER_UNAVAILABLE_MESSAGE);
    }

    public void extendTTL(String topicPattern, Long newTTL) {
        if (topicPattern == null || topicPattern.isBlank()) {
            throw new IllegalArgumentException("topicPattern is required");
        }
        if (newTTL == null || newTTL <= 0) {
            throw new IllegalArgumentException("newTTL must be positive");
        }
        throw new BusinessException(NOT_IMPLEMENTED, PROVIDER_UNAVAILABLE_MESSAGE);
    }

    public LiteTopicQuotaVO getQuota(String namespace) {
        throw new BusinessException(NOT_IMPLEMENTED, PROVIDER_UNAVAILABLE_MESSAGE);
    }

    public LiteTopicCapabilityVO getCapability() {
        return new LiteTopicCapabilityVO(false);
    }
}
