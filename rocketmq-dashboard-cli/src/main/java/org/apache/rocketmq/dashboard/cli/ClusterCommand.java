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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.context.CliConfig;
import org.apache.rocketmq.dashboard.cli.context.CliContext;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.dashboard.cli.security.AuditLogger;
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "cluster", description = "Cluster management operations",
        subcommands = {ClusterCommand.ListCmd.class, ClusterCommand.DescribeCmd.class,
                ClusterCommand.BrokersCmd.class, ClusterCommand.UpdateConfigCmd.class,
                ClusterCommand.CleanCmd.class, ClusterCommand.WipeWritePermCmd.class})
public class ClusterCommand {

    // ==================== L1: Read-only ====================

    @Command(name = "list", description = "List all configured clusters and their health status (L1)")
    static class ListCmd implements Callable<Integer> {
        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            Set<String> clusterNames = ctx.getClusterNames();
            if (clusterNames.isEmpty()) {
                System.out.println("No clusters configured. Use 'rmqctl config add-cluster' to add one.");
                return 0;
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (String name : clusterNames) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("NAME", name);
                CliConfig.ClusterEntry cluster = ctx.getConfig().getClusters().get(name);
                row.put("TYPE", cluster != null ? cluster.getClusterType() : "-");
                row.put("NAMESRV", cluster != null ? cluster.getNamesrvAddr() : "-");

                // Try to connect and get real cluster status
                try (AdminClientHelper admin = AdminClientHelper.connect(name, root)) {
                    ClusterInfo clusterInfo = admin.getClusterInfo();
                    int brokerCount = clusterInfo.getBrokerAddrTable() != null
                            ? clusterInfo.getBrokerAddrTable().size() : 0;
                    row.put("BROKERS", String.valueOf(brokerCount));
                    row.put("STATUS", "ONLINE");
                } catch (Exception e) {
                    row.put("BROKERS", "-");
                    row.put("STATUS", "OFFLINE");
                }
                rows.add(row);
            }

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Total: " + clusterNames.size() + " cluster(s)");
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe cluster topology and capabilities (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name", arity = "0..1")
        String clusterName;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String name = clusterName != null ? clusterName
                    : (root != null && root.getCluster() != null ? root.getCluster() : ctx.getCurrentContext());
            if (name == null) {
                System.err.println("Error: No cluster specified and no current context set.");
                return 1;
            }
            CliConfig.ClusterEntry cluster = ctx.getConfig().getClusters().get(name);
            if (cluster == null) {
                System.err.println("Error: Cluster '" + name + "' not found in configuration.");
                return 1;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("Cluster Name", name);
            result.put("Type", cluster.getClusterType());
            result.put("Namesrv Addr", cluster.getNamesrvAddr());
            result.put("Proxy Addr", cluster.getProxyAddr() != null && !cluster.getProxyAddr().isEmpty()
                    ? cluster.getProxyAddr() : "N/A");

            try (AdminClientHelper admin = AdminClientHelper.connect(name, root)) {
                ClusterInfo clusterInfo = admin.getClusterInfo();
                if (clusterInfo.getBrokerAddrTable() != null) {
                    result.put("Broker Count", String.valueOf(clusterInfo.getBrokerAddrTable().size()));

                    StringBuilder brokers = new StringBuilder();
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        if (brokers.length() > 0) brokers.append(", ");
                        brokers.append(entry.getKey());
                        BrokerData bd = entry.getValue();
                        if (bd.getBrokerAddrs() != null && !bd.getBrokerAddrs().isEmpty()) {
                            String masterAddr = bd.getBrokerAddrs().get(0L);
                            if (masterAddr != null) {
                                brokers.append("(Master: ").append(masterAddr).append(")");
                            }
                            if (bd.getBrokerAddrs().size() > 1) {
                                brokers.append(" + ").append(bd.getBrokerAddrs().size() - 1).append(" slave(s)");
                            }
                        }
                    }
                    result.put("Brokers", brokers.toString());
                }
                result.put("Status", "ONLINE");
            } catch (Exception e) {
                result.put("Status", "OFFLINE");
                result.put("Error", e.getMessage());
            }

            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            return 0;
        }
    }

    @Command(name = "brokers", description = "List brokers in the cluster with runtime details (L1)")
    static class BrokersCmd implements Callable<Integer> {
        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                MQAdminExt mqAdminExt = admin.getMqAdminExt();
                ClusterInfo clusterInfo = admin.getClusterInfo();

                List<Map<String, Object>> rows = new ArrayList<>();
                if (clusterInfo.getBrokerAddrTable() != null) {
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        String brokerName = entry.getKey();
                        BrokerData bd = entry.getValue();
                        if (bd.getBrokerAddrs() == null) continue;

                        for (Map.Entry<Long, String> addrEntry : bd.getBrokerAddrs().entrySet()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("BROKER NAME", brokerName);
                            row.put("ROLE", addrEntry.getKey() == 0L ? "Master" : "Slave-" + addrEntry.getKey());
                            row.put("ADDRESS", addrEntry.getValue());
                            row.put("BROKER ID", String.valueOf(addrEntry.getKey()));

                            // Try to fetch runtime stats for version info
                            try {
                                var stats = mqAdminExt.fetchBrokerRuntimeStats(addrEntry.getValue());
                                if (stats != null && stats.getTable() != null) {
                                    Map<String, String> table = stats.getTable();
                                    row.put("VERSION", table.getOrDefault("brokerVersionDesc", "-"));
                                    row.put("MSG PUT TPS", table.getOrDefault("msgPutTps", "-"));
                                    row.put("MSG GET TPS", table.getOrDefault("msgGetTps", "-"));
                                }
                            } catch (Exception e) {
                                row.put("VERSION", "-");
                                row.put("MSG PUT TPS", "-");
                                row.put("MSG GET TPS", "-");
                            }
                            rows.add(row);
                        }
                    }
                }

                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
                System.out.println();
                System.out.println("Total: " + rows.size() + " broker(s)");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to list brokers - " + e.getMessage());
                return 1;
            }
        }
    }

    // ==================== L2: Controlled mutation (dry-run + --yes) ====================

    @Command(name = "update-config", description = "Update broker configuration property (L2 - default dry-run)")
    static class UpdateConfigCmd implements Callable<Integer> {
        @Option(names = {"--broker"}, description = "Broker name (e.g., broker-a)", required = true)
        String brokerName;

        @Option(names = {"--key"}, description = "Configuration key to update", required = true)
        String configKey;

        @Option(names = {"--value"}, description = "Configuration value to set", required = true)
        String configValue;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        ClusterCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes()) {
                // Execute the update operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    MQAdminExt mqAdminExt = admin.getMqAdminExt();
                    ClusterInfo clusterInfo = admin.getClusterInfo();

                    // Find the broker address
                    BrokerData targetBroker = clusterInfo.getBrokerAddrTable() != null
                            ? clusterInfo.getBrokerAddrTable().get(brokerName) : null;
                    if (targetBroker == null) {
                        System.err.println("Error: Broker '" + brokerName + "' not found in cluster.");
                        System.err.println("Available brokers: "
                                + (clusterInfo.getBrokerAddrTable() != null
                                ? String.join(", ", clusterInfo.getBrokerAddrTable().keySet()) : "none"));
                        return 1;
                    }

                    // Get master address for config update
                    String brokerAddr = targetBroker.getBrokerAddrs() != null
                            ? targetBroker.getBrokerAddrs().get(0L) : null;
                    if (brokerAddr == null) {
                        System.err.println("Error: Broker '" + brokerName + "' has no master address available.");
                        return 1;
                    }

                    // Apply the config change
                    Properties properties = new Properties();
                    properties.setProperty(configKey, configValue);
                    mqAdminExt.updateBrokerConfig(brokerAddr, properties);

                    String clusterName = admin.getClusterName();
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "-",
                            "update-config " + brokerName + " " + configKey + "=" + configValue,
                            "SUCCESS",
                            System.getProperty("user.name"));

                    System.out.println("Broker configuration updated successfully.");
                    System.out.println("  Broker: " + brokerName + " (" + brokerAddr + ")");
                    System.out.println("  " + configKey + " = " + configValue);
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to update broker config - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "update-config " + brokerName + " " + configKey + "=" + configValue,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Dry-run preview
                Map<String, Object> changeDetails = new LinkedHashMap<>();
                changeDetails.put("broker", brokerName);
                changeDetails.put("key", configKey);
                changeDetails.put("value", configValue);
                changeDetails.put("cluster", "(current context)");

                DryRunResult dryRun = DryRunResult.builder()
                        .operation("update broker config " + brokerName + "." + configKey)
                        .willExecute(true)
                        .affectedResources(Arrays.asList(
                                "Broker: " + brokerName,
                                "Config: " + configKey + " = " + configValue))
                        .changeDetails(changeDetails)
                        .estimatedDuration("< 1 second")
                        .warnings(Arrays.asList(
                                "Configuration changes take effect immediately on the broker.",
                                "Some properties may require broker restart to take effect."))
                        .build();
                System.out.println(dryRun.toDisplay());
                System.out.println("Run with --yes to confirm and execute.");
                return 0;
            }
        }
    }

    @Command(name = "clean", description = "Clean unused topics from the cluster (L2 - default dry-run)")
    static class CleanCmd implements Callable<Integer> {
        @Option(names = {"--unused-topics"}, description = "Clean unused topics")
        boolean unusedTopics;

        @Option(names = {"--all-brokers"}, description = "Apply cleanup on all brokers individually")
        boolean allBrokers;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        ClusterCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (!unusedTopics) {
                System.err.println("Error: No cleanup target specified. Use --unused-topics to clean unused topics.");
                System.err.println("Example: rmqctl cluster clean --unused-topics");
                return 1;
            }

            if (root != null && root.isYes()) {
                // Execute the cleanup operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    MQAdminExt mqAdminExt = admin.getMqAdminExt();
                    String clusterName = admin.getClusterName();

                    if (allBrokers) {
                        // Clean on each broker individually
                        List<String> brokerAddrs = admin.getMasterBrokerAddresses();
                        int successCount = 0;
                        for (String addr : brokerAddrs) {
                            try {
                                boolean result = mqAdminExt.cleanUnusedTopicByAddr(addr);
                                if (result) successCount++;
                            } catch (Exception e) {
                                System.err.println("Warning: Failed to clean on broker " + addr + ": " + e.getMessage());
                            }
                        }

                        AuditLogger.getInstance().log(
                                clusterName != null ? clusterName : "-",
                                "clean unused-topics (all brokers)",
                                "SUCCESS (" + successCount + "/" + brokerAddrs.size() + " brokers)",
                                System.getProperty("user.name"));

                        System.out.println("Unused topics cleaned successfully on " + successCount + "/" + brokerAddrs.size() + " broker(s).");
                    } else {
                        // Clean at cluster level
                        boolean result = mqAdminExt.cleanUnusedTopic(clusterName);

                        AuditLogger.getInstance().log(
                                clusterName != null ? clusterName : "-",
                                "clean unused-topics",
                                result ? "SUCCESS" : "NO_CHANGE",
                                System.getProperty("user.name"));

                        System.out.println("Unused topics cleanup " + (result ? "completed successfully." : "returned no changes (no unused topics found)."));
                    }
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to clean unused topics - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "clean unused-topics",
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Dry-run preview
                Map<String, Object> changeDetails = new LinkedHashMap<>();
                changeDetails.put("target", "unused topics");
                changeDetails.put("scope", allBrokers ? "all brokers individually" : "cluster level");
                changeDetails.put("cluster", "(current context)");

                DryRunResult dryRun = DryRunResult.builder()
                        .operation("clean unused topics")
                        .willExecute(true)
                        .affectedResources(Arrays.asList(
                                "All unused topics in the cluster",
                                allBrokers ? "Scope: each broker individually" : "Scope: cluster-level cleanup"))
                        .changeDetails(changeDetails)
                        .estimatedDuration("1-5 seconds")
                        .warnings(Arrays.asList(
                                "Topics without active consumers or producers will be removed.",
                                "This operation is reversible - topics can be recreated if needed."))
                        .build();
                System.out.println(dryRun.toDisplay());
                System.out.println("Run with --yes to confirm and execute.");
                return 0;
            }
        }
    }

    // ==================== L3: Destructive operations (--yes --force) ====================

    @Command(name = "wipe-write-perm", description = "Wipe write permission of a broker from NameServer (L3 - requires --yes --force)")
    static class WipeWritePermCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Broker name to wipe write permission")
        String brokerName;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        ClusterCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes() && root.isForce()) {
                // Execute the wipe operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    MQAdminExt mqAdminExt = admin.getMqAdminExt();

                    // Get the namesrv address from AdminClientHelper
                    String namesrvAddr = admin.getNamesrvAddr();
                    if (namesrvAddr == null || namesrvAddr.isEmpty()) {
                        System.err.println("Error: Unable to determine NameServer address.");
                        return 1;
                    }

                    int wipeResult = mqAdminExt.wipeWritePermOfBroker(namesrvAddr, brokerName);

                    String clusterName = admin.getClusterName();
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "-",
                            "wipe-write-perm " + brokerName,
                            "SUCCESS (wiped " + wipeResult + " NameServer(s))",
                            System.getProperty("user.name"));

                    System.out.println("Write permission wiped for broker '" + brokerName + "'.");
                    System.out.println("  NameServer(s) affected: " + wipeResult);
                    System.out.println("  WARNING: Broker '" + brokerName + "' can no longer accept WRITE requests.");
                    System.out.println("  To restore, restart the broker or update its configuration.");
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to wipe write permission for broker '" + brokerName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "wipe-write-perm " + brokerName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Missing required flags
                System.err.println("ERROR [ERR_L3_BLOCKED]: Wiping write permission for broker '" + brokerName
                        + "' is a dangerous operation (L3).");
                System.err.println("This will prevent the broker from accepting any new WRITE requests.");
                System.err.println("Affected: " + brokerName + " (all producers will be redirected away from this broker)");
                if (root == null || !root.isYes()) {
                    System.err.println("HINT: Add --yes to confirm execution.");
                }
                if (root == null || !root.isForce()) {
                    System.err.println("HINT: Add --force to acknowledge this is a destructive L3 operation.");
                }
                System.err.println("HINT: Use --yes --force to proceed.");
                return 1;
            }
        }
    }
}