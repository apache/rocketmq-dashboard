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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.client.ClientProvider;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Live {@link ClientProvider} backed by the RocketMQ admin API. It discovers producer
 * connections by scanning non-system topics and consumer connections by scanning
 * subscription groups across all brokers in the cluster.
 */
@Slf4j
@Service
@Primary
public class RocketMQClientProvider implements ClientProvider {

    /**
     * Upper bound on the number of non-system topics scanned for producer connections,
     * to avoid issuing an admin call per topic on clusters with a large topic count.
     */
    private static final int MAX_PRODUCER_TOPIC_SCAN = 50;

    private static final long SUBSCRIPTION_GROUP_TIMEOUT_MILLIS = 5000L;

    private final ObjectProvider<DefaultMQAdminExt> adminExtProvider;

    public RocketMQClientProvider(ObjectProvider<DefaultMQAdminExt> adminExtProvider) {
        this.adminExtProvider = adminExtProvider;
    }

    @Override
    public List<ClientConnectionVO> findConnections(String clusterId, String type) {
        DefaultMQAdminExt adminExt = adminExtProvider.getIfAvailable();
        if (adminExt == null) {
            log.warn("DefaultMQAdminExt is not configured, returning empty client connection list");
            return List.of();
        }

        ClientType clientType = parseType(type);
        List<ClientConnectionVO> connections = new ArrayList<>();
        if (clientType == null || clientType == ClientType.Producer) {
            connections.addAll(findProducerConnections(adminExt, clusterId));
        }
        if (clientType == null || clientType == ClientType.Consumer) {
            connections.addAll(findConsumerConnections(adminExt, clusterId));
        }
        return connections;
    }

    private List<ClientConnectionVO> findProducerConnections(DefaultMQAdminExt adminExt, String clusterId) {
        List<ClientConnectionVO> result = new ArrayList<>();
        Set<String> topics;
        try {
            TopicList topicList = adminExt.fetchAllTopicList();
            topics = topicList == null ? Set.of() : topicList.getTopicList();
        } catch (Exception e) {
            log.warn("Failed to fetch topic list for producer connection scan", e);
            return result;
        }

        int scanned = 0;
        for (String topic : topics) {
            if (isSystemTopic(topic)) {
                continue;
            }
            if (scanned >= MAX_PRODUCER_TOPIC_SCAN) {
                log.info("Producer connection scan capped at {} non-system topics", MAX_PRODUCER_TOPIC_SCAN);
                break;
            }
            scanned++;
            try {
                ProducerConnection producerConnection = adminExt.examineProducerConnectionInfo(null, topic);
                if (producerConnection == null || producerConnection.getConnectionSet() == null) {
                    continue;
                }
                for (Connection connection : producerConnection.getConnectionSet()) {
                    if (connection == null) {
                        continue;
                    }
                    result.add(toConnectionVO(connection, ClientType.Producer, topic, topic, clusterId));
                }
            } catch (Exception e) {
                log.warn("Failed to examine producer connection for topic={}, skipping", topic, e);
            }
        }
        return result;
    }

    private List<ClientConnectionVO> findConsumerConnections(DefaultMQAdminExt adminExt, String clusterId) {
        List<ClientConnectionVO> result = new ArrayList<>();
        Set<String> groups = collectSubscriptionGroups(adminExt);
        for (String group : groups) {
            if (isSystemGroup(group)) {
                continue;
            }
            try {
                ConsumerConnection consumerConnection = adminExt.examineConsumerConnectionInfo(group);
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
        return result;
    }

    private Set<String> collectSubscriptionGroups(DefaultMQAdminExt adminExt) {
        Set<String> groups = new LinkedHashSet<>();
        ClusterInfo clusterInfo;
        try {
            clusterInfo = adminExt.examineBrokerClusterInfo();
        } catch (Exception e) {
            log.warn("Failed to fetch cluster info for consumer connection scan", e);
            return groups;
        }
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return groups;
        }
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null) {
                continue;
            }
            String brokerAddr = brokerData.selectBrokerAddr();
            if (brokerAddr == null) {
                continue;
            }
            try {
                SubscriptionGroupWrapper wrapper =
                        adminExt.getAllSubscriptionGroup(brokerAddr, SUBSCRIPTION_GROUP_TIMEOUT_MILLIS);
                if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                    groups.addAll(wrapper.getSubscriptionGroupTable().keySet());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch subscription groups from broker={}, skipping", brokerAddr, e);
            }
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
            log.warn("Unknown client type filter: {}", type);
            return null;
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

    private boolean isSystemTopic(String topic) {
        if (topic == null) {
            return true;
        }
        return topic.startsWith("RMQ_SYS_")
                || topic.startsWith("SCHEDULE_TOPIC_")
                || topic.startsWith("%RETRY%")
                || topic.startsWith("%DLQ%")
                || topic.startsWith("TBW102")
                || topic.startsWith("SELF_TEST_")
                || topic.startsWith("DefaultCluster")
                || topic.startsWith("broker_")
                || topic.startsWith("OFFSET_MOVED_")
                || topic.startsWith("CID_RMQ_SYS_")
                || topic.startsWith("TRANS_CHECK_")
                || topic.startsWith("BenchmarkTest");
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
}
