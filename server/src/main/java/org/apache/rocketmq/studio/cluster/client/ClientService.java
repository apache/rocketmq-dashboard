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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientProvider clientProvider;

    public List<ClientConnectionVO> listConnections(String instanceId, String clusterId, String type) {
        log.info("Listing client connections, instanceId={}, clusterId={}, type={}", instanceId, clusterId, type);
        return nullToEmpty(clientProvider.findConnections(
                requireInstanceId(instanceId), normalizeFilter(clusterId), normalizeFilter(type)));
    }

    public List<ClientConnectionVO> listConnectionsAt(String namesrvAddr, String clusterId, String type) {
        log.info("Listing client connections, namesrvAddr={}, clusterId={}, type={}", namesrvAddr, clusterId, type);
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(400, "namesrvAddr is required");
        }
        return nullToEmpty(clientProvider.findConnectionsAt(
                namesrvAddr.trim(), normalizeFilter(clusterId), normalizeFilter(type)));
    }

    /** Providers may report an unavailable lookup as {@code null}; the API contract is a list. */
    private static List<ClientConnectionVO> nullToEmpty(List<ClientConnectionVO> connections) {
        return connections == null ? List.of() : connections;
    }

    private String requireInstanceId(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        return instanceId.trim();
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
