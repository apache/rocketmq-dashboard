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
package org.apache.rocketmq.studio.cluster.client;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProducerConnectionService {

    private static final int DEFAULT_PRODUCER_GROUP_SELECTOR_LIMIT = 20;
    private static final int MAX_PRODUCER_GROUP_SELECTOR_LIMIT = 100;

    private final ClientProvider clientProvider;

    public List<ProducerConnectionVO> listConnections(String instanceId, String topic, String producerGroup) {
        log.info("Listing producer connections, instanceId={}, topic={}, producerGroup={}",
                instanceId, topic, producerGroup);
        String normalizedInstanceId = requireFilter(instanceId, "instanceId");
        String normalizedTopic = requireFilter(topic, "topic");
        String normalizedProducerGroup = normalizeOptionalFilter(producerGroup);
        return clientProvider.findProducerConnections(normalizedInstanceId, normalizedTopic, normalizedProducerGroup)
                .stream()
                .map(this::toProducerConnection)
                .toList();
    }

    public List<String> listProducerGroups(String instanceId, String topic, String query, Integer limit) {
        String normalizedInstanceId = requireFilter(instanceId, "instanceId");
        return clientProvider.findProducerGroups(
                normalizedInstanceId,
                normalizeOptionalFilter(topic),
                normalizeOptionalFilter(query),
                normalizeSelectorLimit(limit));
    }

    private ProducerConnectionVO toProducerConnection(ClientConnectionVO connection) {
        return ProducerConnectionVO.builder()
                .clientId(connection.getClientId())
                .clientAddr(connection.getAddress())
                .topic(connection.getGroupOrTopic())
                .producerGroup(connection.getProducerGroup())
                .language(connection.getLanguage() == null ? null : connection.getLanguage().name())
                .versionDesc(connection.getVersion())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeOptionalFilter(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int normalizeSelectorLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PRODUCER_GROUP_SELECTOR_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_PRODUCER_GROUP_SELECTOR_LIMIT);
    }

    private String requireFilter(String value, String fieldName) {
        if (!hasText(value)) {
            throw new BusinessException(400, fieldName + " is required");
        }
        return value.trim();
    }
}
