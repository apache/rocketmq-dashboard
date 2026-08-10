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
package org.apache.rocketmq.studio.cluster.nameserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NameServerConfigDiffService {

    static final String SAFE_CONFIG_KEYS_RESOURCE = "/cluster/nameserver-safe-config-keys.yml";

    private static final List<String> SAFE_CONFIG_KEYS = loadSafeConfigKeys();

    private static List<String> loadSafeConfigKeys() {
        try (InputStream input = NameServerConfigDiffService.class
                .getResourceAsStream(SAFE_CONFIG_KEYS_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing NameServer config diff keys resource: " + SAFE_CONFIG_KEYS_RESOURCE);
            }
            JsonNode keysNode = new ObjectMapper(new YAMLFactory())
                    .readTree(input).path("keys");
            if (!keysNode.isArray() || keysNode.isEmpty()) {
                throw new IllegalStateException(
                        "NameServer config diff keys resource must define a non-empty 'keys' list: "
                                + SAFE_CONFIG_KEYS_RESOURCE);
            }
            List<String> keys = new ArrayList<>();
            keysNode.forEach(node -> keys.add(node.asText()));
            return List.copyOf(keys);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load NameServer config diff keys resource: " + SAFE_CONFIG_KEYS_RESOURCE,
                    exception);
        }
    }

    private final ClusterService clusterService;
    private final MqAdminExtFactory adminFactory;

    public NameServerConfigDiffVO compare(String clusterId) {
        String normalizedClusterId = requireClusterId(clusterId);
        return compare(normalizedClusterId, clusterService.getCluster(normalizedClusterId));
    }

    public NameServerConfigDiffVO compare(String clusterId, String instanceId) {
        String normalizedClusterId = requireClusterId(clusterId);
        return compare(normalizedClusterId,
                clusterService.getCluster(normalizedClusterId, normalizeInstanceId(instanceId)));
    }

    private NameServerConfigDiffVO compare(String normalizedClusterId, ClusterVO cluster) {
        List<String> addresses = collectNameServerAddresses(cluster);
        if (addresses.isEmpty()) {
            throw new BusinessException(409,
                    "Cluster has no NameServer endpoints: " + normalizedClusterId);
        }

        String connectionEndpoint = connectionEndpoint(cluster, addresses);
        Map<String, Properties> reachableConfigs = new LinkedHashMap<>();
        List<NameServerConfigDiffVO.NodeStatusVO> nodes = new ArrayList<>();

        for (String address : addresses) {
            try {
                Properties config = readConfig(connectionEndpoint, address);
                reachableConfigs.put(address, config);
                nodes.add(NameServerConfigDiffVO.NodeStatusVO.builder()
                        .address(address)
                        .reachable(true)
                        .build());
            } catch (BusinessException exception) {
                nodes.add(NameServerConfigDiffVO.NodeStatusVO.builder()
                        .address(address)
                        .reachable(false)
                        .build());
            }
        }

        List<NameServerConfigDiffVO.ConfigDifferenceVO> differences =
                findDifferences(reachableConfigs);
        int reachableNodeCount = reachableConfigs.size();
        return NameServerConfigDiffVO.builder()
                .cluster(normalizedClusterId)
                .complete(reachableNodeCount == addresses.size())
                .driftDetected(!differences.isEmpty())
                .nodeCount(addresses.size())
                .reachableNodeCount(reachableNodeCount)
                .comparedKeys(SAFE_CONFIG_KEYS)
                .nodes(nodes)
                .differences(differences)
                .build();
    }

    private Properties readConfig(String connectionEndpoint, String address) {
        return adminFactory.execute(connectionEndpoint, null, admin -> {
            Map<String, Properties> configs = admin.getNameServerConfig(List.of(address));
            Properties config = configs == null ? null : configs.get(address);
            if (config == null) {
                throw new BusinessException(502,
                        "NameServer returned no configuration: " + address);
            }
            return config;
        });
    }

    private List<NameServerConfigDiffVO.ConfigDifferenceVO> findDifferences(
            Map<String, Properties> configs) {
        if (configs.size() < 2) {
            return List.of();
        }

        List<NameServerConfigDiffVO.ConfigDifferenceVO> differences = new ArrayList<>();
        for (String key : SAFE_CONFIG_KEYS) {
            List<NameServerConfigDiffVO.ConfigValueVO> values = configs.entrySet().stream()
                    .map(entry -> configValue(entry.getKey(), entry.getValue(), key))
                    .toList();
            long distinctValues = values.stream()
                    .map(value -> value.isConfigured() ? value.getValue() : null)
                    .distinct()
                    .count();
            if (distinctValues > 1) {
                differences.add(NameServerConfigDiffVO.ConfigDifferenceVO.builder()
                        .key(key)
                        .values(values)
                        .build());
            }
        }
        return differences;
    }

    private NameServerConfigDiffVO.ConfigValueVO configValue(
            String address,
            Properties config,
            String key) {
        String value = config.getProperty(key);
        return NameServerConfigDiffVO.ConfigValueVO.builder()
                .address(address)
                .configured(value != null)
                .value(value)
                .build();
    }

    private List<String> collectNameServerAddresses(ClusterVO cluster) {
        Stream<String> declared = cluster.getNameServers() == null
                ? Stream.empty()
                : cluster.getNameServers().stream()
                        .filter(node -> node != null)
                        .map(NameServerVO::getAddr);
        Stream<String> endpointAddresses = splitEndpoint(cluster.getEndpoint()).stream();
        return Stream.concat(declared, endpointAddresses)
                .filter(address -> address != null && !address.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private String connectionEndpoint(ClusterVO cluster, List<String> addresses) {
        if (cluster.getEndpoint() != null && !cluster.getEndpoint().isBlank()) {
            return cluster.getEndpoint().trim();
        }
        return String.join(";", addresses);
    }

    private List<String> splitEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return List.of();
        }
        return Stream.of(endpoint.split("[;,]"))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .toList();
    }

    private String requireClusterId(String clusterId) {
        if (clusterId == null || clusterId.isBlank()) {
            throw new BusinessException(400, "cluster is required");
        }
        return clusterId.trim();
    }

    private String normalizeInstanceId(String instanceId) {
        return instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
    }
}
