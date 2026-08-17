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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientProvider clientProvider;

    private static final int MAX_PAGE_SIZE = 100;
    private static final Comparator<ClientConnectionVO> CONNECTION_ORDER = Comparator
            .comparing(ClientConnectionVO::getClusterName, Comparator.nullsFirst(String::compareTo))
            .thenComparing(connection -> connection.getType() == null ? null : connection.getType().name(),
                    Comparator.nullsFirst(String::compareTo))
            .thenComparing(ClientConnectionVO::getGroupOrTopic, Comparator.nullsFirst(String::compareTo))
            .thenComparing(ClientConnectionVO::getClientId, Comparator.nullsFirst(String::compareTo))
            .thenComparing(ClientConnectionVO::getAddress, Comparator.nullsFirst(String::compareTo));

    public PageResult<ClientConnectionVO> listConnections(String instanceId, String clusterId, String type,
                                                           int page, int pageSize) {
        validatePage(page, pageSize);
        log.info("Listing client connections, instanceId={}, clusterId={}, type={}, page={}, pageSize={}",
                instanceId, clusterId, type, page, pageSize);
        List<ClientConnectionVO> connections = clientProvider.findConnections(
                        requireInstanceId(instanceId), normalizeFilter(clusterId), normalizeFilter(type)).stream()
                .sorted(CONNECTION_ORDER)
                .toList();
        int total = connections.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return PageResult.of(connections.subList(from, to), total, page, pageSize);
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

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
