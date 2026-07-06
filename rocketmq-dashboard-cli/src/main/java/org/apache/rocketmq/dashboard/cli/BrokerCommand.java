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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "broker", description = "Broker management commands",
        subcommands = {BrokerCommand.ListCmd.class, BrokerCommand.DescribeCmd.class,
                BrokerCommand.ConfigCmd.class})

/** CLI commands for broker operations: list brokers, describe broker details, view/update broker configuration. */
public class BrokerCommand {

    @ParentCommand
    RmqctlCommand root;

    @Command(name = "list", description = "List all brokers in the cluster (L1)")
    static class ListCmd implements Callable<Integer> {
        @ParentCommand
        BrokerCommand parent;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @Override
        public Integer call() throws Exception {
            MQAdminExt admin = AdminClientHelper.connectRaw(cluster, parent.root);
            try {
                ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
                Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();
                if (brokerAddrTable == null || brokerAddrTable.isEmpty()) {
                    System.out.println("No brokers found in the cluster.");
                    return 0;
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map.Entry<String, BrokerData> entry : brokerAddrTable.entrySet()) {
                    String brokerName = entry.getKey();
                    BrokerData brokerData = entry.getValue();
                    Map<Long, String> brokerAddrs = brokerData.getBrokerAddrs();
                    if (brokerAddrs == null || brokerAddrs.isEmpty()) {
                        continue;
                    }

                    for (Map.Entry<Long, String> addrEntry : brokerAddrs.entrySet()) {
                        Long brokerId = addrEntry.getKey();
                        String brokerAddr = addrEntry.getValue();
                        String role = (brokerId == 0L) ? "Master" : "Slave";

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("BROKER NAME", brokerName);
                        row.put("ADDRESS", brokerAddr);
                        row.put("ROLE", role);
                        row.put("STATUS", "ONLINE");

                        try {
                            KVTable runtimeStats = admin.fetchBrokerRuntimeStats(brokerAddr);
                            Map<String, String> stats = runtimeStats.getTable();
                            row.put("VERSION", stats.getOrDefault("brokerVersionDesc", "N/A"));
                            row.put("TOPICS", stats.getOrDefault("topicCount", "N/A"));
                        } catch (Exception e) {
                            row.put("VERSION", "N/A");
                            row.put("TOPICS", "N/A");
                        }

                        rows.add(row);
                    }
                }

                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            } finally {
                admin.shutdown();
            }
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe a broker in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @ParentCommand
        BrokerCommand parent;

        @Parameters(index = "0", description = "Broker name")
        String brokerName;

        @Override
        public Integer call() throws Exception {
            MQAdminExt admin = AdminClientHelper.connectRaw(null, parent.root);
            try {
                ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
                Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();
                if (brokerAddrTable == null || !brokerAddrTable.containsKey(brokerName)) {
                    System.err.println("Error: Broker '" + brokerName + "' not found in the cluster.");
                    return 1;
                }

                BrokerData brokerData = brokerAddrTable.get(brokerName);
                Map<Long, String> brokerAddrs = brokerData.getBrokerAddrs();
                if (brokerAddrs == null || brokerAddrs.isEmpty()) {
                    System.err.println("Error: No addresses found for broker '" + brokerName + "'.");
                    return 1;
                }

                // Use master (brokerId=0) address for runtime stats
                String masterAddr = brokerAddrs.get(0L);
                if (masterAddr == null) {
                    // Fall back to first available address
                    masterAddr = brokerAddrs.values().iterator().next();
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("Broker Name", brokerName);
                result.put("Cluster", brokerData.getCluster() != null ? brokerData.getCluster() : "N/A");
                result.put("Address", masterAddr);

                try {
                    KVTable runtimeStats = admin.fetchBrokerRuntimeStats(masterAddr);
                    Map<String, String> stats = runtimeStats.getTable();

                    result.put("Version", stats.getOrDefault("brokerVersionDesc", "N/A"));
                    result.put("Role", "Master");
                    result.put("Status", "ONLINE");
                    result.put("Boot Time", stats.getOrDefault("bootTimestamp", "N/A"));
                    result.put("Uptime", stats.getOrDefault("runtime", "N/A"));

                    String putTps = stats.getOrDefault("putTps", "N/A");
                    result.put("In TPS", putTps);
                    result.put("Out TPS", stats.getOrDefault("getTransferedTps", "N/A"));

                    result.put("CommitLog Max Offset", stats.getOrDefault("commitLogMaxOffset", "N/A"));
                    result.put("CommitLog Min Offset", stats.getOrDefault("commitLogMinOffset", "N/A"));

                    result.put("Topic Count", stats.getOrDefault("topicCount", "N/A"));
                    result.put("Producer Count", stats.getOrDefault("producerCount", "N/A"));
                    result.put("Consumer Count", stats.getOrDefault("consumerCount", "N/A"));

                    result.put("Put Message Average Speed", stats.getOrDefault("putMessageAverageSpeed", "N/A"));
                    result.put("Put Message Size", stats.getOrDefault("putMessageSize", "N/A"));
                } catch (Exception e) {
                    result.put("Status", "UNREACHABLE");
                    result.put("Error", e.getMessage());
                }

                System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            } finally {
                admin.shutdown();
            }
            return 0;
        }
    }

    @Command(name = "config", description = "Show broker runtime configuration (L1)")
    static class ConfigCmd implements Callable<Integer> {
        @ParentCommand
        BrokerCommand parent;

        @Parameters(index = "0", description = "Broker name")
        String brokerName;

        @Option(names = {"--filter"}, description = "Filter config keys (e.g., 'flush,commitlog')")
        String filter;

        @Override
        public Integer call() throws Exception {
            MQAdminExt admin = AdminClientHelper.connectRaw(null, parent.root);
            try {
                ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
                Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();
                if (brokerAddrTable == null || !brokerAddrTable.containsKey(brokerName)) {
                    System.err.println("Error: Broker '" + brokerName + "' not found in the cluster.");
                    return 1;
                }

                BrokerData brokerData = brokerAddrTable.get(brokerName);
                Map<Long, String> brokerAddrs = brokerData.getBrokerAddrs();
                if (brokerAddrs == null || brokerAddrs.isEmpty()) {
                    System.err.println("Error: No addresses found for broker '" + brokerName + "'.");
                    return 1;
                }

                // Use master (brokerId=0) address
                String brokerAddr = brokerAddrs.get(0L);
                if (brokerAddr == null) {
                    brokerAddr = brokerAddrs.values().iterator().next();
                }

                Properties props = admin.getBrokerConfig(brokerAddr);

                // Build sorted config entries, applying filter if specified
                String[] filterKeys = null;
                if (filter != null && !filter.isEmpty()) {
                    filterKeys = filter.split(",");
                }

                TreeMap<String, String> sortedConfig = new TreeMap<>();
                for (String key : props.stringPropertyNames()) {
                    if (filterKeys != null) {
                        boolean matched = false;
                        String keyLower = key.toLowerCase();
                        for (String f : filterKeys) {
                            if (keyLower.contains(f.trim().toLowerCase())) {
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
                            continue;
                        }
                    }
                    sortedConfig.put(key, props.getProperty(key));
                }

                if (sortedConfig.isEmpty()) {
                    System.out.println("No configuration entries found" +
                            (filter != null ? " matching filter '" + filter + "'" : "") + ".");
                    return 0;
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map.Entry<String, String> entry : sortedConfig.entrySet()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("CONFIG KEY", entry.getKey());
                    row.put("VALUE", entry.getValue());
                    rows.add(row);
                }

                System.out.println("Broker: " + brokerName);
                System.out.println("Address: " + brokerAddr);
                if (filter != null) {
                    System.out.println("Filter: " + filter);
                }
                System.out.println();
                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            } finally {
                admin.shutdown();
            }
            return 0;
        }
    }
}
