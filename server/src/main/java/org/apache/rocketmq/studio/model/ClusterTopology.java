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
package org.apache.rocketmq.studio.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified cluster topology model
 * Hide differences between different cluster architectures
 */
public class ClusterTopology {

    /**
     * Cluster name
     */
    private String clusterName;

    /**
     * NameServer address list
     */
    private List<String> namesrvAddresses;

    /**
     * NameServer node list
     */
    private List<NodeInfo> namesrvNodes;

    /**
     * Broker node list
     */
    private List<NodeInfo> brokerNodes;

    /**
     * Proxy node list (5.0 architecture)
     */
    private List<NodeInfo> proxyNodes;

    /**
     * Node mapping table (for quick lookup)
     */
    private Map<String, NodeInfo> nodeMap;

    public ClusterTopology() {
        this.namesrvAddresses = new ArrayList<>();
        this.namesrvNodes = new ArrayList<>();
        this.brokerNodes = new ArrayList<>();
        this.proxyNodes = new ArrayList<>();
        this.nodeMap = new HashMap<>();
    }

    /**
     * Add node
     */
    public void addNode(String nodeName, Long nodeId, String nodeAddress, String nodeType) {
        NodeInfo node = new NodeInfo();
        node.setNodeName(nodeName);
        node.setNodeId(nodeId);
        node.setNodeAddress(nodeAddress);
        node.setNodeType(nodeType);
        node.setClusterName(clusterName);

        String key = nodeType + "-" + nodeName + "-" + nodeId;
        nodeMap.put(key, node);

        switch (nodeType) {
            case "NAMESRV":
                namesrvNodes.add(node);
                break;
            case "BROKER":
                brokerNodes.add(node);
                break;
            case "PROXY":
                proxyNodes.add(node);
                break;
        }
    }

    /**
     * Get total node count
     */
    public int getTotalNodeCount() {
        return namesrvNodes.size() + brokerNodes.size() + proxyNodes.size();
    }

    /**
     * Get master broker count
     */
    public int getMasterBrokerCount() {
        return (int) brokerNodes.stream()
            .filter(node -> node.getNodeId() != null && node.getNodeId() == 0)
            .count();
    }

    /**
     * Get slave broker count
     */
    public int getSlaveBrokerCount() {
        return (int) brokerNodes.stream()
            .filter(node -> node.getNodeId() != null && node.getNodeId() > 0)
            .count();
    }


    public static class NodeInfo {
        private String nodeName;
        private Long nodeId;
        private String nodeAddress;
        private String nodeType;
        private String clusterName;
        private String status;
        private Long version;
        private Map<String, Object> metadata;

        public NodeInfo() {
            this.metadata = new HashMap<>();
            this.status = "UNKNOWN";
        }

        public boolean isMaster() {
            return nodeId != null && nodeId == 0;
        }

        public boolean isOnline() {
            return "ONLINE".equals(status);
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public Long getNodeId() {
            return nodeId;
        }

        public void setNodeId(Long nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeAddress() {
            return nodeAddress;
        }

        public void setNodeAddress(String nodeAddress) {
            this.nodeAddress = nodeAddress;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public List<String> getNamesrvAddresses() {
        return namesrvAddresses;
    }

    public void setNamesrvAddresses(List<String> namesrvAddresses) {
        this.namesrvAddresses = namesrvAddresses;
    }

    public List<NodeInfo> getNamesrvNodes() {
        return namesrvNodes;
    }

    public void setNamesrvNodes(List<NodeInfo> namesrvNodes) {
        this.namesrvNodes = namesrvNodes;
    }

    public List<NodeInfo> getBrokerNodes() {
        return brokerNodes;
    }

    public void setBrokerNodes(List<NodeInfo> brokerNodes) {
        this.brokerNodes = brokerNodes;
    }

    public List<NodeInfo> getProxyNodes() {
        return proxyNodes;
    }

    public void setProxyNodes(List<NodeInfo> proxyNodes) {
        this.proxyNodes = proxyNodes;
    }

    public Map<String, NodeInfo> getNodeMap() {
        return nodeMap;
    }

    public void setNodeMap(Map<String, NodeInfo> nodeMap) {
        this.nodeMap = nodeMap;
    }
}
