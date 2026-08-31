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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BrokerConfigDiffService {

    private static final List<ConfigField> COMPARED_FIELDS = List.of(
            new ConfigField("flushDiskType", "flushDiskType"),
            new ConfigField("autoCreateTopicEnable", "autoCreateTopicEnable"),
            new ConfigField("autoCreateSubscriptionGroup", "autoCreateSubscriptionGroup"),
            new ConfigField("maxMessageSize", "maxMessageSize"),
            new ConfigField("msgTraceTopicName", "msgTraceTopicName"),
            new ConfigField("deleteWhen", "deleteWhen"),
            new ConfigField("fileReservedTime", "fileReservedTime"),
            new ConfigField("writeQueueNums", "defaultTopicQueueNums"),
            new ConfigField("readQueueNums", "defaultTopicQueueNums"),
            new ConfigField("brokerPermission", "brokerPermission"));

    private final ClusterService clusterService;
    private final RocketMQBrokerConfigService brokerConfigService;

    public BrokerConfigDiffVO compare(String clusterId, String instanceId) {
        String normalizedClusterId = requireClusterId(clusterId);
        String normalizedInstanceId = normalizeInstanceId(instanceId);
        ClusterVO cluster = normalizedInstanceId == null
                ? clusterService.getCluster(normalizedClusterId)
                : clusterService.getCluster(normalizedClusterId, normalizedInstanceId);
        List<BrokerTarget> brokers = collectBrokerTargets(cluster);
        if (brokers.isEmpty()) {
            throw new BusinessException(409, "Cluster has no broker endpoints: " + normalizedClusterId);
        }

        Map<BrokerTarget, ClusterConfigVO> reachableConfigs = new LinkedHashMap<>();
        List<BrokerConfigDiffVO.BrokerStatusVO> statuses = new ArrayList<>();
        for (BrokerTarget broker : brokers) {
            try {
                ClusterConfigVO config = brokerConfigService.getBrokerConfig(broker.address(), normalizedInstanceId);
                reachableConfigs.put(broker, config);
                statuses.add(BrokerConfigDiffVO.BrokerStatusVO.builder()
                        .name(broker.name())
                        .address(broker.address())
                        .reachable(true)
                        .build());
            } catch (BusinessException exception) {
                statuses.add(BrokerConfigDiffVO.BrokerStatusVO.builder()
                        .name(broker.name())
                        .address(broker.address())
                        .reachable(false)
                        .build());
            }
        }

        List<BrokerConfigDiffVO.ConfigDifferenceVO> differences = findDifferences(reachableConfigs);
        return BrokerConfigDiffVO.builder()
                .cluster(normalizedClusterId)
                .complete(reachableConfigs.size() == brokers.size())
                .driftDetected(!differences.isEmpty())
                .brokerCount(brokers.size())
                .reachableBrokerCount(reachableConfigs.size())
                .comparedFields(COMPARED_FIELDS.stream().map(ConfigField::field).toList())
                .brokers(statuses)
                .differences(differences)
                .build();
    }

    private List<BrokerTarget> collectBrokerTargets(ClusterVO cluster) {
        if (cluster.getBrokers() == null) {
            return List.of();
        }
        Map<String, BrokerTarget> targets = new LinkedHashMap<>();
        cluster.getBrokers().stream()
                .filter(Objects::nonNull)
                .filter(broker -> StringUtils.hasText(broker.getAddr()))
                .forEach(broker -> {
                    String address = broker.getAddr().trim();
                    targets.putIfAbsent(address, new BrokerTarget(
                            StringUtils.hasText(broker.getName()) ? broker.getName().trim() : address,
                            address));
                });
        return List.copyOf(targets.values());
    }

    private List<BrokerConfigDiffVO.ConfigDifferenceVO> findDifferences(
            Map<BrokerTarget, ClusterConfigVO> configs) {
        if (configs.size() < 2) {
            return List.of();
        }

        List<BrokerConfigDiffVO.ConfigDifferenceVO> differences = new ArrayList<>();
        for (ConfigField field : COMPARED_FIELDS) {
            List<BrokerConfigDiffVO.ConfigValueVO> values = configs.entrySet().stream()
                    .map(entry -> configValue(entry.getKey(), entry.getValue(), field))
                    .toList();
            long distinctValues = values.stream()
                    .map(value -> value.isConfigured() ? value.getValue() : null)
                    .distinct()
                    .count();
            if (distinctValues > 1) {
                differences.add(BrokerConfigDiffVO.ConfigDifferenceVO.builder()
                        .field(field.field())
                        .brokerProperty(field.brokerProperty())
                        .values(values)
                        .build());
            }
        }
        return differences;
    }

    private BrokerConfigDiffVO.ConfigValueVO configValue(
            BrokerTarget broker,
            ClusterConfigVO config,
            ConfigField field) {
        String value = field.value(config);
        return BrokerConfigDiffVO.ConfigValueVO.builder()
                .brokerName(broker.name())
                .address(broker.address())
                .configured(value != null)
                .value(value)
                .build();
    }

    private String requireClusterId(String clusterId) {
        if (!StringUtils.hasText(clusterId)) {
            throw new BusinessException(400, "cluster is required");
        }
        return clusterId.trim();
    }

    private String normalizeInstanceId(String instanceId) {
        return StringUtils.hasText(instanceId) ? instanceId.trim() : null;
    }

    private record BrokerTarget(String name, String address) {
    }

    private record ConfigField(String field, String brokerProperty) {
        String value(ClusterConfigVO config) {
            if (config == null) {
                return null;
            }
            Object value = switch (field) {
                case "flushDiskType" -> config.getFlushDiskType();
                case "autoCreateTopicEnable" -> config.isAutoCreateTopicEnable();
                case "autoCreateSubscriptionGroup" -> config.isAutoCreateSubscriptionGroup();
                case "maxMessageSize" -> config.getMaxMessageSize();
                case "msgTraceTopicName" -> config.getMsgTraceTopicName();
                case "deleteWhen" -> config.getDeleteWhen();
                case "fileReservedTime" -> config.getFileReservedTime();
                case "writeQueueNums" -> config.getWriteQueueNums();
                case "readQueueNums" -> config.getReadQueueNums();
                case "brokerPermission" -> config.getBrokerPermission();
                default -> null;
            };
            return value == null ? null : String.valueOf(value);
        }
    }
}
