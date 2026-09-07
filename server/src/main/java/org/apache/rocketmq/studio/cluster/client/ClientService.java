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
import org.apache.rocketmq.studio.cluster.proxy.admin.Rip2ProxyAdminClient;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientProvider clientProvider;
    private final Rip2ProxyAdminClient rip2ProxyAdminClient;

    public List<ClientConnectionVO> listConnections(String instanceId, String clusterId, String type) {
        log.info("Listing client connections, instanceId={}, clusterId={}, type={}", instanceId, clusterId, type);
        return mergeRip2Clients(
                clientProvider.findConnections(requireInstanceId(instanceId), normalizeFilter(clusterId), normalizeFilter(type)),
                normalizeFilter(clusterId), normalizeFilter(type));
    }

    public List<ClientConnectionVO> listConnectionsAt(String namesrvAddr, String clusterId, String type) {
        log.info("Listing client connections, namesrvAddr={}, clusterId={}, type={}", namesrvAddr, clusterId, type);
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(400, "namesrvAddr is required");
        }
        return mergeRip2Clients(
                clientProvider.findConnectionsAt(namesrvAddr.trim(), normalizeFilter(clusterId), normalizeFilter(type)),
                normalizeFilter(clusterId), normalizeFilter(type));
    }

    /**
     * RIP-2 fix: gRPC SDK clients never register with the broker's remoting admin, so the
     * remoting-based view above cannot see them. Merge the consumers surfaced by the proxy's
     * RIP-2 admin surface ({@code listConsumerConnection} on the dedicated admin port) into
     * the result. Duplicates (same clientId) keep the remoting entry; failures degrade to
     * the remoting view unchanged.
     */
    private List<ClientConnectionVO> mergeRip2Clients(List<ClientConnectionVO> base, String clusterId, String type) {
        if ("Producer".equalsIgnoreCase(type)) {
            // The RIP-2 admin surface only reports consumer connections; producers would
            // add nothing here.
            return base;
        }
        List<Rip2ProxyAdminClient.GrpcConsumer> grpcConsumers;
        try {
            grpcConsumers = rip2ProxyAdminClient.listGrpcConsumers();
        } catch (Throwable t) {
            log.warn("RIP-2 client merge skipped: {}", t.getMessage());
            return base;
        }
        if (grpcConsumers.isEmpty()) {
            return base;
        }
        Set<String> seenClientIds = new HashSet<>();
        for (ClientConnectionVO vo : base) {
            if (vo.getClientId() != null) {
                seenClientIds.add(vo.getClientId());
            }
        }
        List<ClientConnectionVO> merged = new ArrayList<>(base);
        int added = 0;
        for (Rip2ProxyAdminClient.GrpcConsumer gc : grpcConsumers) {
            if (!seenClientIds.add(gc.clientId())) {
                continue;
            }
            merged.add(ClientConnectionVO.builder()
                    .clientId(gc.clientId())
                    .type(ClientType.Consumer)
                    .groupOrTopic(gc.group())
                    .protocol(Protocol.gRPC)
                    .address(gc.egressIp() == null || gc.egressIp().isBlank() ? gc.hostname() : gc.egressIp())
                    .language(mapLanguage(gc.language()))
                    .version(gc.version())
                    .clusterName(clusterId)
                    .partial(false)
                    .build());
            added++;
        }
        log.info("RIP-2 merge: {} gRPC consumer client(s) added to the client view", added);
        return merged;
    }

    private ClientLanguage mapLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return ClientLanguage.Java;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        for (ClientLanguage lang : ClientLanguage.values()) {
            if (lang.name().toUpperCase(Locale.ROOT).equals(n)
                    || lang.name().toUpperCase(Locale.ROOT).startsWith(n)) {
                return lang;
            }
        }
        return ClientLanguage.Java;
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
