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
package org.apache.rocketmq.dashboard.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "client", description = "Client management commands",
        subcommands = {ClientCommand.ListCmd.class, ClientCommand.DescribeCmd.class})

/** CLI commands for client management: list connected clients and describe individual client details. */
public class ClientCommand {

    @ParentCommand
    RmqctlCommand root;

    /**
     * Holds aggregated client information collected from consumer and producer connections.
     */
    private static class ClientInfo {
        String clientId;
        String address;
        String language;
        int version;
        String role;
        Set<String> topics = new LinkedHashSet<>();
        Set<String> consumerGroups = new LinkedHashSet<>();
        Map<String, SubscriptionData> subscriptions = new LinkedHashMap<>();
    }

    @Command(name = "list", description = "List all connected clients (L1)")
    static class ListCmd implements Callable<Integer> {
        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @Option(names = {"--group"}, description = "Filter by consumer group")
        String group;

        @ParentCommand
        ClientCommand parent;

        @Override
        public Integer call() throws Exception {
            MQAdminExt admin = null;
            try {
                admin = AdminClientHelper.connectRaw(cluster, parent.root);

                // Collect all consumer groups across all brokers
                Set<String> allGroups = new TreeSet<>();
                var clusterInfo = admin.examineBrokerClusterInfo();
                if (clusterInfo.getBrokerAddrTable() != null) {
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        BrokerData brokerData = entry.getValue();
                        String brokerAddr = brokerData.selectBrokerAddr();
                        if (brokerAddr == null) {
                            continue;
                        }
                        try {
                            SubscriptionGroupWrapper wrapper = admin.getAllSubscriptionGroup(brokerAddr, 10000);
                            if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                                allGroups.addAll(wrapper.getSubscriptionGroupTable().keySet());
                            }
                        } catch (Exception e) {
                            // Skip unreachable brokers
                        }
                    }
                }

                // Collect consumer connections: iterate each group and gather Connection objects
                Map<String, ClientInfo> clientMap = new LinkedHashMap<>();

                for (String groupName : allGroups) {
                    try {
                        ConsumerConnection cc = admin.examineConsumerConnectionInfo(groupName);
                        if (cc == null || cc.getConnectionSet() == null) {
                            continue;
                        }

                        // Determine subscribed topics for this group
                        Set<String> groupTopics = new LinkedHashSet<>();
                        if (cc.getSubscriptionTable() != null) {
                            groupTopics.addAll(cc.getSubscriptionTable().keySet());
                        }

                        for (Connection conn : cc.getConnectionSet()) {
                            String cid = conn.getClientId();
                            if (cid == null) {
                                continue;
                            }
                            ClientInfo info = clientMap.computeIfAbsent(cid, k -> {
                                ClientInfo ci = new ClientInfo();
                                ci.clientId = conn.getClientId();
                                ci.address = conn.getClientAddr();
                                ci.language = conn.getLanguage() != null ? conn.getLanguage().name() : "UNKNOWN";
                                ci.version = conn.getVersion();
                                ci.role = "Consumer";
                                return ci;
                            });
                            info.topics.addAll(groupTopics);
                            info.consumerGroups.add(groupName);
                        }
                    } catch (Exception e) {
                        // Group may have no active consumers; skip gracefully
                    }
                }

                // Collect producer connections from each broker
                if (clusterInfo.getBrokerAddrTable() != null) {
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        BrokerData brokerData = entry.getValue();
                        String brokerAddr = brokerData.selectBrokerAddr();
                        if (brokerAddr == null) {
                            continue;
                        }
                        try {
                            var producerTable = admin.getAllProducerInfo(brokerAddr);
                            if (producerTable == null || producerTable.getData() == null) {
                                continue;
                            }
                            for (Map.Entry<String, ?> producerEntry : producerTable.getData().entrySet()) {
                                String topicName = producerEntry.getKey();
                                Object producerList = producerEntry.getValue();
                                if (!(producerList instanceof List)) {
                                    continue;
                                }
                                for (Object item : (List<?>) producerList) {
                                    // ProducerInfo has getClientId() and getClientAddr() via reflection-safe access
                                    try {
                                        String cid = (String) item.getClass().getMethod("getClientId").invoke(item);
                                        String caddr = (String) item.getClass().getMethod("getClientAddr").invoke(item);
                                        if (cid == null) {
                                            continue;
                                        }
                                        ClientInfo info = clientMap.get(cid);
                                        if (info != null) {
                                            // Already known as consumer; upgrade to Producer/Consumer
                                            if ("Consumer".equals(info.role)) {
                                                info.role = "Producer/Consumer";
                                            }
                                            info.topics.add(topicName);
                                        } else {
                                            ClientInfo newInfo = new ClientInfo();
                                            newInfo.clientId = cid;
                                            newInfo.address = caddr;
                                            newInfo.role = "Producer";
                                            newInfo.topics.add(topicName);
                                            clientMap.put(cid, newInfo);
                                        }
                                    } catch (Exception e) {
                                        // Skip entries that don't match expected shape
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip unreachable brokers for producer info
                        }
                    }
                }

                // Apply group filter if specified
                List<ClientInfo> clients = new ArrayList<>(clientMap.values());
                if (group != null) {
                    clients.removeIf(ci -> !ci.consumerGroups.contains(group));
                }

                // Build output rows
                List<Map<String, Object>> rows = new ArrayList<>();
                for (ClientInfo ci : clients) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("CLIENT ID", ci.clientId);
                    row.put("ADDRESS", ci.address != null ? ci.address : "N/A");
                    row.put("VERSION", String.valueOf(ci.version));
                    row.put("ROLE", ci.role);
                    row.put("CONNECTED SINCE", "N/A");
                    row.put("TOPICS", String.valueOf(ci.topics.size()));
                    rows.add(row);
                }

                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
                System.out.println();
                System.out.println("Total: " + clients.size() + " connected client(s)");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to list clients - " + e.getMessage());
                return 1;
            } finally {
                if (admin != null) {
                    admin.shutdown();
                }
            }
        }
    }

    @Command(name = "describe", description = "Describe a client in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Client ID")
        String clientId;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        ClientCommand parent;

        @Override
        public Integer call() throws Exception {
            MQAdminExt admin = null;
            try {
                admin = AdminClientHelper.connectRaw(cluster, parent.root);

                // Collect all consumer groups across all brokers
                Set<String> allGroups = new TreeSet<>();
                var clusterInfo = admin.examineBrokerClusterInfo();
                if (clusterInfo.getBrokerAddrTable() != null) {
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        BrokerData brokerData = entry.getValue();
                        String brokerAddr = brokerData.selectBrokerAddr();
                        if (brokerAddr == null) {
                            continue;
                        }
                        try {
                            SubscriptionGroupWrapper wrapper = admin.getAllSubscriptionGroup(brokerAddr, 10000);
                            if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                                allGroups.addAll(wrapper.getSubscriptionGroupTable().keySet());
                            }
                        } catch (Exception e) {
                            // Skip unreachable brokers
                        }
                    }
                }

                // Search through consumer connections to find the target client
                ClientInfo found = null;
                for (String groupName : allGroups) {
                    try {
                        ConsumerConnection cc = admin.examineConsumerConnectionInfo(groupName);
                        if (cc == null || cc.getConnectionSet() == null) {
                            continue;
                        }
                        for (Connection conn : cc.getConnectionSet()) {
                            if (clientId.equals(conn.getClientId())) {
                                if (found == null) {
                                    found = new ClientInfo();
                                    found.clientId = conn.getClientId();
                                    found.address = conn.getClientAddr();
                                    found.language = conn.getLanguage() != null ? conn.getLanguage().name() : "UNKNOWN";
                                    found.version = conn.getVersion();
                                    found.role = "Consumer";
                                }
                                found.consumerGroups.add(groupName);
                                if (cc.getSubscriptionTable() != null) {
                                    found.subscriptions.putAll(cc.getSubscriptionTable());
                                    found.topics.addAll(cc.getSubscriptionTable().keySet());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip groups with connection errors
                    }
                }

                // Also check producer connections to detect Producer or Producer/Consumer role
                Set<String> producerTopics = new LinkedHashSet<>();
                if (clusterInfo.getBrokerAddrTable() != null) {
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        BrokerData brokerData = entry.getValue();
                        String brokerAddr = brokerData.selectBrokerAddr();
                        if (brokerAddr == null) {
                            continue;
                        }
                        try {
                            var producerTable = admin.getAllProducerInfo(brokerAddr);
                            if (producerTable == null || producerTable.getData() == null) {
                                continue;
                            }
                            for (Map.Entry<String, ?> producerEntry : producerTable.getData().entrySet()) {
                                String topicName = producerEntry.getKey();
                                Object producerList = producerEntry.getValue();
                                if (!(producerList instanceof List)) {
                                    continue;
                                }
                                for (Object item : (List<?>) producerList) {
                                    try {
                                        String cid = (String) item.getClass().getMethod("getClientId").invoke(item);
                                        if (clientId.equals(cid)) {
                                            producerTopics.add(topicName);
                                            if (found == null) {
                                                found = new ClientInfo();
                                                found.clientId = cid;
                                                String caddr = (String) item.getClass().getMethod("getClientAddr").invoke(item);
                                                found.address = caddr;
                                                found.role = "Producer";
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Skip entries that don't match expected shape
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip unreachable brokers for producer info
                        }
                    }
                }

                // Upgrade role if client is both producer and consumer
                if (found != null && !producerTopics.isEmpty()) {
                    if ("Consumer".equals(found.role)) {
                        found.role = "Producer/Consumer";
                    }
                    found.topics.addAll(producerTopics);
                }

                // Build output
                Map<String, Object> result = new LinkedHashMap<>();
                if (found != null) {
                    result.put("Client ID", found.clientId);
                    result.put("Address", found.address != null ? found.address : "N/A");
                    result.put("Language", found.language);
                    result.put("Version", String.valueOf(found.version));
                    result.put("Role", found.role);
                    result.put("Connected Since", "N/A");
                    result.put("Protocol", found.language != null && found.language.contains("JAVA") ? "Remoting" : "gRPC");
                    result.put("Last Heartbeat", "N/A");
                    if (!found.subscriptions.isEmpty()) {
                        result.put("Subscriptions", String.join(", ", found.subscriptions.keySet()));
                    } else if (!found.topics.isEmpty()) {
                        result.put("Subscriptions", String.join(", ", found.topics));
                    } else {
                        result.put("Subscriptions", "N/A");
                    }
                    result.put("Consume Group", found.consumerGroups.isEmpty() ? "N/A" : String.join(", ", found.consumerGroups));
                    result.put("Consume Latency", "N/A");
                } else {
                    result.put("Client ID", clientId);
                    result.put("Status", "NOT FOUND");
                    result.put("Details", "Client '" + clientId + "' is not connected as a consumer or producer on any broker.");
                }

                System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to describe client '" + clientId + "' - " + e.getMessage());
                return 1;
            } finally {
                if (admin != null) {
                    admin.shutdown();
                }
            }
        }
    }
}
