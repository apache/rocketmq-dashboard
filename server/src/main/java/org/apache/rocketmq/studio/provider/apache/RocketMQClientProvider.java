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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerInfo;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerTableInfo;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.client.ClientProvider;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.common.util.MqResponseCodes;
import org.apache.rocketmq.studio.common.util.SystemGroupFilter;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Live {@link ClientProvider} backed by the RocketMQ admin API. It discovers producer
 * connections from each broker's producer table and consumer connections by scanning
 * subscription groups across all brokers in the cluster.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RocketMQClientProvider implements ClientProvider {

    private static final long SUBSCRIPTION_GROUP_TIMEOUT_MILLIS = 5000L;

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final MqAdminExtFactory adminFactory;
    private final ProxyConsumerResolver proxyConsumerResolver;

    @Override
    public List<ClientConnectionVO> findConnections(String instanceId, String clusterId, String type) {
        return runtimeAdminClientResolver.execute(instanceId, adminExt -> findConnections(adminExt, clusterId, type));
    }

    @Override
    public List<ClientConnectionVO> findConnectionsAt(String namesrvAddr, String clusterId, String type) {
        return adminFactory.execute(namesrvAddr, null,
                adminExt -> findConnections(adminExt, clusterId, type));
    }

    private List<ClientConnectionVO> findConnections(MQAdminExt adminExt, String clusterId, String type) {
        ClientType clientType = parseType(type);
        List<ClientConnectionVO> connections = new ArrayList<>();
        if (clientType == null || clientType == ClientType.Producer) {
            connections.addAll(findAllProducerConnections(adminExt, clusterId));
        }
        if (clientType == null || clientType == ClientType.Consumer) {
            connections.addAll(findConsumerConnections(adminExt, clusterId));
        }
        return connections;
    }

    @Override
    public List<ClientConnectionVO> findProducerConnections(String instanceId, String topic, String producerGroup) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> findProducerConnections(adminExt, topic, producerGroup));
    }

    @Override
    public List<String> findProducerGroups(String instanceId, String topic, String query, int limit) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> findProducerGroups(adminExt, topic, query, limit));
    }

    private List<String> findProducerGroups(MQAdminExt adminExt, String topic, String query, int limit) {
        BrokerTopology topology = discoverBrokerTopology(adminExt, null, "producer group selector");
        if (topology.brokerAddresses().isEmpty()) {
            return List.of();
        }
        String normalizedQuery = query == null ? null : query.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        int successfulBrokers = 0;
        for (String brokerAddress : topology.brokerAddresses()) {
            try {
                ProducerTableInfo producerTable = adminExt.getAllProducerInfo(brokerAddress);
                successfulBrokers++;
                collectProducerGroups(groups, producerTable, normalizedQuery);
            } catch (Exception e) {
                log.warn("Failed to fetch producer groups from broker={}, skipping", brokerAddress, e);
            }
        }
        if (successfulBrokers == 0) {
            throw new BusinessException(502, "Failed to query producer groups from all brokers");
        }
        return groups.stream()
                .sorted(Comparator.naturalOrder())
                .limit(limit)
                .toList();
    }

    private List<ClientConnectionVO> findProducerConnections(MQAdminExt adminExt, String topic, String producerGroup) {
        if (producerGroup == null || producerGroup.isBlank()) {
            return findProducerConnectionsForActiveGroups(adminExt, topic);
        }
        return findProducerConnectionsForGroup(adminExt, topic, producerGroup);
    }

    private List<ClientConnectionVO> findProducerConnectionsForActiveGroups(MQAdminExt adminExt, String topic) {
        List<String> producerGroups = findProducerGroups(adminExt, topic, null, Integer.MAX_VALUE);
        if (producerGroups.isEmpty()) {
            return List.of();
        }
        List<ClientConnectionVO> connections = new ArrayList<>();
        int successfulGroupQueries = 0;
        for (String producerGroup : producerGroups) {
            try {
                connections.addAll(findProducerConnectionsForGroup(adminExt, topic, producerGroup));
                successfulGroupQueries++;
            } catch (BusinessException e) {
                log.warn("Failed to query producer connections for group={}, skipping", producerGroup, e);
            }
        }
        if (successfulGroupQueries == 0) {
            throw new BusinessException(502, "Failed to query producer connections from all groups");
        }
        return connections;
    }

    private List<ClientConnectionVO> findProducerConnectionsForGroup(
            MQAdminExt adminExt, String topic, String producerGroup) {
        try {
            ProducerConnection producerConnection =
                    adminExt.examineProducerConnectionInfo(producerGroup, topic);
            if (producerConnection == null || producerConnection.getConnectionSet() == null) {
                return List.of();
            }
            return producerConnection.getConnectionSet().stream()
                    .filter(Objects::nonNull)
                    .map(connection -> toConnectionVO(
                            connection, ClientType.Producer, topic, producerGroup, null, false))
                    .toList();
        } catch (MQClientException e) {
            if (isTopicNotExist(e) || isGroupConnectionAbsent(e)) {
                // A missing topic route or an offline producer group are normal "nothing
                // here" outcomes — the client page should show an empty list, not a 502
                // that looks like a failure.
                return List.of();
            }
            throw new BusinessException(502,
                    "Failed to query producer connections: " + rootMessage(e));
        } catch (Exception e) {
            throw new BusinessException(502,
                    "Failed to query producer connections: " + rootMessage(e));
        }
    }

    private boolean isGroupConnectionAbsent(MQClientException e) {
        if (MqResponseCodes.hasResponseCode(e, ResponseCode.CONSUMER_NOT_ONLINE)) {
            // rocketmq-tools examineConsumerConnectionInfo throws CONSUMER_NOT_ONLINE (206)
            // for a group with no online connections.
            return true;
        }
        String message = e.getErrorMessage() == null ? e.getMessage() : e.getErrorMessage();
        return message != null && (message.contains("Not found the consumer group connection")
                || message.contains("Not found the producer group connection"));
    }

    private boolean isTopicNotExist(MQClientException e) {
        if (e.getResponseCode() == ResponseCode.TOPIC_NOT_EXIST) {
            return true;
        }
        String message = e.getErrorMessage() == null ? e.getMessage() : e.getErrorMessage();
        return message != null && message.contains("route info of topic")
                || message != null && message.contains("route info not found");
    }

    private List<ClientConnectionVO> findAllProducerConnections(
            MQAdminExt adminExt, String clusterId) {
        BrokerTopology topology = discoverBrokerTopology(adminExt, clusterId, "producer connections");
        Map<String, ClientConnectionVO> connections = new LinkedHashMap<>();
        int successfulBrokers = 0;
        for (String brokerAddress : topology.brokerAddresses()) {
            try {
                ProducerTableInfo producerTable = adminExt.getAllProducerInfo(brokerAddress);
                successfulBrokers++;
                addProducerConnections(connections, producerTable, topology.clusterFor(brokerAddress));
            } catch (Exception e) {
                log.warn("Failed to fetch producer connections from broker={}, skipping", brokerAddress, e);
            }
        }
        if (!topology.brokerAddresses().isEmpty() && successfulBrokers == 0) {
            throw new BusinessException(502, "Failed to query producer connections from all brokers");
        }
        return new ArrayList<>(connections.values());
    }

    private BrokerTopology discoverBrokerTopology(MQAdminExt adminExt, String clusterId, String operation) {
        ClusterInfo clusterInfo;
        try {
            clusterInfo = adminExt.examineBrokerClusterInfo();
        } catch (Exception e) {
            throw new BusinessException(502,
                    "Failed to discover brokers for " + operation + ": " + rootMessage(e));
        }
        Map<String, String> clusterByAddress = new LinkedHashMap<>();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return new BrokerTopology(clusterByAddress);
        }
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null || brokerData.getBrokerAddrs() == null
                    || brokerData.getBrokerAddrs().isEmpty()) {
                continue;
            }
            String brokerAddress = brokerData.selectBrokerAddr();
            String brokerCluster = brokerData.getCluster();
            if (brokerAddress != null && !brokerAddress.isBlank()
                    && (clusterId == null || clusterId.equals(brokerCluster))) {
                clusterByAddress.putIfAbsent(brokerAddress, brokerCluster);
            }
        }
        return new BrokerTopology(clusterByAddress);
    }

    private void addProducerConnections(
            Map<String, ClientConnectionVO> connections,
            ProducerTableInfo producerTable,
            String clusterId) {
        if (producerTable == null || producerTable.getData() == null) {
            return;
        }
        producerTable.getData().forEach((producerGroup, producerInfos) -> {
            if (producerGroup == null || producerGroup.isBlank() || producerInfos == null) {
                return;
            }
            for (ProducerInfo producerInfo : producerInfos) {
                if (producerInfo == null) {
                    continue;
                }
                String key = Objects.toString(clusterId, "") + '\0'
                        + producerGroup + '\0'
                        + Objects.toString(producerInfo.getClientId(), "") + '\0'
                        + Objects.toString(producerInfo.getRemoteIP(), "");
                connections.putIfAbsent(key, toConnectionVO(producerInfo, producerGroup, clusterId));
            }
        });
    }

    private void collectProducerGroups(
            LinkedHashSet<String> groups,
            ProducerTableInfo producerTable,
            String normalizedQuery) {
        if (producerTable == null || producerTable.getData() == null) {
            return;
        }
        producerTable.getData().keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(group -> !group.isEmpty())
                .filter(group -> normalizedQuery == null
                        || group.toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .forEach(groups::add);
    }

    private ClientConnectionVO toConnectionVO(
            ProducerInfo producerInfo, String producerGroup, String clusterId) {
        String remoteIp = producerInfo.getRemoteIP();
        if (remoteIp != null && remoteIp.startsWith("/")) {
            remoteIp = remoteIp.substring(1);
        }
        return ClientConnectionVO.builder()
                .clientId(producerInfo.getClientId())
                .type(ClientType.Producer)
                .groupOrTopic(producerGroup)
                .producerGroup(producerGroup)
                .protocol(Protocol.Remoting)
                .address(remoteIp)
                .language(mapLanguage(producerInfo.getLanguage()))
                .version(MQVersion.getVersionDesc(producerInfo.getVersion()))
                .clusterName(clusterId)
                .build();
    }

    private List<ClientConnectionVO> findConsumerConnections(MQAdminExt adminExt, String clusterId) {
        Map<String, ClientConnectionVO> result = new LinkedHashMap<>();
        Map<ConsumerGroup, List<String>> groups = collectSubscriptionGroups(adminExt, clusterId);
        Map<String, List<String>> proxiesByBroker = new LinkedHashMap<>();
        int attemptedGroupQueries = 0;
        int successfulGroupQueries = 0;
        for (Map.Entry<ConsumerGroup, List<String>> entry : groups.entrySet()) {
            ConsumerGroup group = entry.getKey();
            if (isSystemGroup(group.name())) {
                continue;
            }
            attemptedGroupQueries++;
            boolean complete = true;
            LinkedHashSet<String> proxies = new LinkedHashSet<>();
            for (String broker : entry.getValue()) {
                complete &= addConsumerConnections(adminExt, broker, group, false, result);
                try {
                    List<String> addresses = proxiesByBroker.get(broker);
                    if (addresses == null) {
                        addresses = proxyConsumerResolver.discoverProxyAddresses(adminExt, broker);
                        proxiesByBroker.put(broker, addresses);
                    }
                    proxies.addAll(addresses);
                } catch (MQClientException | MQBrokerException | RemotingException e) {
                    if (MqResponseCodes.hasResponseCode(e, ResponseCode.CONSUMER_NOT_ONLINE)) {
                        proxiesByBroker.put(broker, List.of());
                    } else {
                        log.warn("Failed to discover proxies for broker={}", broker, e);
                        complete = false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(502, "Consumer connection query interrupted");
                }
            }
            for (String proxy : proxies) {
                complete &= addConsumerConnections(adminExt, proxy, group, true, result);
            }
            if (complete) {
                successfulGroupQueries++;
            }
        }
        // An empty response cannot carry the existing per-row partial flag.
        if (successfulGroupQueries < attemptedGroupQueries && result.isEmpty()) {
            throw new BusinessException(502, "Failed to query consumer connections from all groups");
        }
        if (successfulGroupQueries < attemptedGroupQueries) {
            result.values().forEach(connection -> connection.setPartial(true));
        }
        return new ArrayList<>(result.values());
    }

    private boolean addConsumerConnections(MQAdminExt admin, String address, ConsumerGroup group,
                                            boolean proxy, Map<String, ClientConnectionVO> result) {
        try {
            // Explicit addresses keep both Broker and Proxy queries inside the selected cluster.
            ConsumerConnection connections = admin.examineConsumerConnectionInfo(group.name(), address);
            if (connections != null && connections.getConnectionSet() != null) {
                for (Connection connection : connections.getConnectionSet()) {
                    if (connection == null) {
                        continue;
                    }
                    ClientConnectionVO row = toConnectionVO(connection, ClientType.Consumer,
                            group.name(), null, group.cluster(), proxy);
                    String key = group.cluster() + '\0' + group.name() + '\0'
                            + connection.getClientId() + '\0' + connection.getClientAddr();
                    result.putIfAbsent(key, row);
                }
            }
            return true;
        } catch (MQClientException | MQBrokerException | RemotingException e) {
            if (MqResponseCodes.hasResponseCode(e, ResponseCode.CONSUMER_NOT_ONLINE)) {
                return true;
            }
            log.warn("Failed to examine consumer connection for group={} via {}", group.name(), address, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "Consumer connection query interrupted");
        }
    }

    private record ConsumerGroup(String name, String cluster) {
    }

    private Map<ConsumerGroup, List<String>> collectSubscriptionGroups(MQAdminExt adminExt, String clusterId) {
        Map<ConsumerGroup, List<String>> groups = new LinkedHashMap<>();
        BrokerTopology topology = discoverBrokerTopology(adminExt, clusterId, "consumer connections");
        int attemptedBrokerQueries = 0;
        int successfulBrokerQueries = 0;
        for (String brokerAddr : topology.brokerAddresses()) {
            try {
                attemptedBrokerQueries++;
                SubscriptionGroupWrapper wrapper =
                        adminExt.getAllSubscriptionGroup(brokerAddr, SUBSCRIPTION_GROUP_TIMEOUT_MILLIS);
                successfulBrokerQueries++;
                if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                    wrapper.getSubscriptionGroupTable().keySet().forEach(group ->
                            groups.computeIfAbsent(new ConsumerGroup(group, topology.clusterFor(brokerAddr)),
                                    key -> new ArrayList<>()).add(brokerAddr));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch subscription groups from broker={}, skipping", brokerAddr, e);
            }
        }
        if (attemptedBrokerQueries > 0 && successfulBrokerQueries == 0) {
            throw new BusinessException(502, "Failed to query subscription groups from all brokers");
        }
        return groups;
    }

    private ClientConnectionVO toConnectionVO(Connection connection, ClientType type, String groupOrTopic,
                                              String producerGroup, String clusterId, boolean proxy) {
        return ClientConnectionVO.builder()
                .clientId(connection.getClientId())
                .type(type)
                .groupOrTopic(groupOrTopic)
                .producerGroup(producerGroup)
                // The Proxy response has no protocol; gRPC may carry a fixed compatibility version.
                .protocol(proxy ? null : Protocol.Remoting)
                .address(connection.getClientAddr())
                .language(mapLanguage(connection.getLanguage()))
                .version(proxy ? null : MQVersion.getVersionDesc(connection.getVersion()))
                .clusterName(clusterId)
                .build();
    }

    private ClientType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ClientType.valueOf(type.trim());
        } catch (IllegalArgumentException e) {
            String normalized = type.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("prod")) {
                return ClientType.Producer;
            }
            if (normalized.startsWith("cons")) {
                return ClientType.Consumer;
            }
            throw new BusinessException(400, "Unknown client type filter: " + type);
        }
    }

    private ClientLanguage mapLanguage(LanguageCode languageCode) {
        if (languageCode == null) {
            return null;
        }
        switch (languageCode) {
            case JAVA:
                return ClientLanguage.Java;
            case GO:
                return ClientLanguage.Go;
            case PYTHON:
                return ClientLanguage.Python;
            case RUST:
                return ClientLanguage.Rust;
            case CPP:
                return ClientLanguage.Cpp;
            case DOTNET:
                return ClientLanguage.CSharp;
            case PHP:
                return ClientLanguage.PHP;
            default:
                return null;
        }
    }

    private boolean isSystemGroup(String group) {
        return SystemGroupFilter.isSystem(group);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private record BrokerTopology(Map<String, String> clusterByAddress) {
        private List<String> brokerAddresses() {
            return List.copyOf(clusterByAddress.keySet());
        }

        private String clusterFor(String brokerAddress) {
            return clusterByAddress.get(brokerAddress);
        }
    }
}
