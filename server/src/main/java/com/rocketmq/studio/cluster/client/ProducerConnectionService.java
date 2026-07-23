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
package com.rocketmq.studio.cluster.client;

import com.rocketmq.studio.common.domain.enums.ClientType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProducerConnectionService {

    private final ClientService clientService;

    public List<ProducerConnectionVO> listConnections(String topic, String producerGroup) {
        log.info("Listing producer connections, topic={}, producerGroup={}", topic, producerGroup);
        return clientService.listConnections(null, ClientType.Producer.name()).stream()
                .filter(connection -> matchesFilter(connection, topic, producerGroup))
                .map(this::toProducerConnection)
                .toList();
    }

    private boolean matchesFilter(ClientConnectionVO connection, String topic, String producerGroup) {
        if (hasText(topic)) {
            return topic.equals(connection.getGroupOrTopic());
        }
        if (hasText(producerGroup)) {
            return producerGroup.equals(connection.getGroupOrTopic());
        }
        return true;
    }

    private ProducerConnectionVO toProducerConnection(ClientConnectionVO connection) {
        return ProducerConnectionVO.builder()
                .clientId(connection.getClientId())
                .clientAddr(connection.getAddress())
                .language(connection.getLanguage() == null ? null : connection.getLanguage().name())
                .versionDesc(connection.getVersion())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
