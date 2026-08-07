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
import org.apache.rocketmq.tools.admin.MQAdminExt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final MqAdminExtFactory adminFactory;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final RocketMQProperties properties;

    @Override
    public List<ClientConnectionVO> findConnections(String instanceId, String clusterId, String type) {
        return runtimeAdminClientResolver.execute(instanceId, adminExt -> findConnections(adminExt, clusterId, type));
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
    public List<ClientConnectionVO> findProducerConnections(String topic, String producerGroup) {
        String namesrvAddr = properties.getNamesrvAddr();
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(501, "Client connection provider is not configured");
        }
        return adminFactory.execute(namesrvAddr, null, admin -> {
            try {
                ProducerConnection producerConnection =
                        admin.examineProducerConnectionInfo(producerGroup, topic);
                if (producerConnection == null || producerConnection.getConnectionSet() == null) {
                    return List.<ClientConnectionVO>of();
                }
                return producerConnection.getConnectionSet().stream()
                        .filter(Objects::nonNull)
                        .map(connection -> toConnectionVO(
                                connection, ClientType.Producer, topic, producerGroup, null))
                        .toList();
            } catch (MQClientException e) {
                if (isTopicNotExist(e)) {
                    // A non-existent topic is a normal "nothing here" outcome — the client
                    // page should show an empty list, not a 502 that looks like a failure.
                    return List.<ClientConnectionVO>of();
                }
                throw new BusinessException(502,
                        "Failed to query producer connections: " + rootMessage(e));
            } catch (Exception e) {
                throw new BusinessException(502,
                        "Failed to query producer connections: " + rootMessage(e));
            }
        });
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
        Set<String> brokerAddresses = collectProducerBrokerAddresses(adminExt);
        Map<String, ClientConnectionVO> connections = new LinkedHashMap<>();
        int successfulBrokers = 0;
        for (String brokerAddress : brokerAddresses) {
            try {
                ProducerTableInfo producerTable = adminExt.getAllProducerInfo(brokerAddress);
                successfulBrokers++;
                addProducerConnections(connections, producerTable, clusterId);
            } catch (Exception e) {
                log.warn("Failed to fetch producer connections from broker={}, skipping", brokerAddress, e);
            }
        }
        if (!brokerAddresses.isEmpty() && successfulBrokers == 0) {
            throw new BusinessException(502, "Failed to query producer connections from all brokers");
        }
        return new ArrayList<>(connections.values());
    }

    private Set<String> collectProducerBrokerAddresses(MQAdminExt adminExt) {
        ClusterInfo clusterInfo;
        try {
            clusterInfo = adminExt.examineBrokerClusterInfo();
        } catch (Exception e) {
            throw new BusinessException(502,
                    "Failed to discover brokers for producer connections: " + rootMessage(e));
        }
        Set<String> addresses = new LinkedHashSet<>();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return addresses;
        }
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null) {
                continue;
            }
            String brokerAddress = brokerData.selectBrokerAddr();
            if (brokerAddress != null && !brokerAddress.isBlank()) {
                addresses.add(brokerAddress);
            }
        }
        return addresses;
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
                String key = producerGroup + '\0'
                        + Objects.toString(producerInfo.getClientId(), "") + '\0'
                        + Objects.toString(producerInfo.getRemoteIP(), "");
                connections.putIfAbsent(key, toConnectionVO(producerInfo, producerGroup, clusterId));
            }
        });
    }

    private ClientConnectionVO toConnectionVO(
            ProducerInfo producerInfo, String producerGroup, String clusterId) {
        return ClientConnectionVO.builder()
                .clientId(producerInfo.getClientId())
                .type(ClientType.Producer)
                .groupOrTopic(producerGroup)
                .producerGroup(producerGroup)
                .protocol(Protocol.Remoting)
                .address(producerInfo.getRemoteIP())
                .language(mapLanguage(producerInfo.getLanguage()))
                .version(String.valueOf(producerInfo.getVersion()))
                .clusterName(clusterId)
                .build();
    }

    private List<ClientConnectionVO> findConsumerConnections(MQAdminExt adminExt, String clusterId) {
        List<ClientConnectionVO> result = new ArrayList<>();
        Set<String> groups = collectSubscriptionGroups(adminExt);
        int successfulGroupQueries = 0;
        for (String group : groups) {
            if (isSystemGroup(group)) {
                continue;
            }
            try {
                ConsumerConnection consumerConnection = adminExt.examineConsumerConnectionInfo(group);
                successfulGroupQueries++;
                if (consumerConnection == null || consumerConnection.getConnectionSet() == null) {
                    continue;
                }
                for (Connection connection : consumerConnection.getConnectionSet()) {
                    if (connection == null) {
                        continue;
                    }
                    result.add(toConnectionVO(connection, ClientType.Consumer, group, null, clusterId));
                }
            } catch (Exception e) {
                log.warn("Failed to examine consumer connection for group={}, skipping", group, e);
            }
        }
        if (!groups.isEmpty() && successfulGroupQueries == 0) {
            throw new BusinessException(502, "Failed to query consumer connections from all groups");
        }
        return result;
    }

    private Set<String> collectSubscriptionGroups(MQAdminExt adminExt) {
        Set<String> groups = new LinkedHashSet<>();
        ClusterInfo clusterInfo;
        try {
            clusterInfo = adminExt.examineBrokerClusterInfo();
        } catch (Exception e) {
            log.warn("Failed to fetch cluster info for consumer connection scan", e);
            throw new BusinessException(502, "Failed to discover brokers for consumer connections: "
                    + rootMessage(e));
        }
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return groups;
        }
        int attemptedBrokerQueries = 0;
        int successfulBrokerQueries = 0;
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null) {
                continue;
            }
            String brokerAddr = brokerData.selectBrokerAddr();
            if (brokerAddr == null) {
                continue;
            }
            try {
                attemptedBrokerQueries++;
                SubscriptionGroupWrapper wrapper =
                        adminExt.getAllSubscriptionGroup(brokerAddr, SUBSCRIPTION_GROUP_TIMEOUT_MILLIS);
                successfulBrokerQueries++;
                if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                    groups.addAll(wrapper.getSubscriptionGroupTable().keySet());
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
                                              String producerGroup, String clusterId) {
        return ClientConnectionVO.builder()
                .clientId(connection.getClientId())
                .type(type)
                .groupOrTopic(groupOrTopic)
                .producerGroup(producerGroup)
                .protocol(Protocol.Remoting)
                .address(connection.getClientAddr())
                .language(mapLanguage(connection.getLanguage()))
                .version(String.valueOf(connection.getVersion()))
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
        if (group == null) {
            return true;
        }
        return group.startsWith("CID_RMQ_SYS_")
                || group.startsWith("CID_ONSAPI_")
                || group.startsWith("TOOLS_CONSUMER")
                || group.startsWith("FILTERSRV_CONSUMER")
                || group.startsWith("CID_SYS_")
                || group.startsWith("%RETRY%")
                || group.startsWith("SELF_TEST_")
                || group.startsWith("CID_HOUSEKEEPING");
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
}
