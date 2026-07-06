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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.dashboard.cli.security.AuditLogger;
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "group", description = "Consumer group management commands",
        subcommands = {GroupCommand.ListCmd.class, GroupCommand.DescribeCmd.class,
                GroupCommand.CreateCmd.class, GroupCommand.UpdateCmd.class,
                GroupCommand.ResetOffsetCmd.class, GroupCommand.DeleteCmd.class})

/** CLI commands for consumer group management: list, describe, create (L2), update (L2), reset-offset (L2), delete (L3). */
public class GroupCommand {

    @Command(name = "list", description = "List all consumer groups (L1)")
    static class ListCmd implements Callable<Integer> {
        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                MQAdminExt mqAdminExt = admin.getMqAdminExt();

                // Collect all subscription groups across all brokers
                Set<String> allGroups = new TreeSet<>();
                Map<String, SubscriptionGroupConfig> groupConfigMap = new LinkedHashMap<>();

                var clusterInfo = admin.getClusterInfo();
                if (clusterInfo.getBrokerAddrTable() != null) {
                    for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                        BrokerData brokerData = entry.getValue();
                        String brokerAddr = brokerData.selectBrokerAddr();
                        if (brokerAddr == null) {
                            continue;
                        }
                        try {
                            SubscriptionGroupWrapper wrapper = mqAdminExt.getAllSubscriptionGroup(brokerAddr, 10000);
                            if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                                for (Map.Entry<String, SubscriptionGroupConfig> ge : wrapper.getSubscriptionGroupTable().entrySet()) {
                                    allGroups.add(ge.getKey());
                                    groupConfigMap.putIfAbsent(ge.getKey(), ge.getValue());
                                }
                            }
                        } catch (Exception e) {
                            // Skip unreachable brokers
                        }
                    }
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                for (String groupName : allGroups) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("GROUP NAME", groupName);
                    SubscriptionGroupConfig config = groupConfigMap.get(groupName);
                    if (config != null) {
                        String consumeMode;
                        if (!config.isConsumeBroadcastEnable() && config.isConsumeEnable()) {
                            consumeMode = "Clustering";
                        } else if (config.isConsumeBroadcastEnable()) {
                            consumeMode = "Broadcasting";
                        } else {
                            consumeMode = "Disabled";
                        }
                        row.put("CONSUME MODE", consumeMode);
                        row.put("RETRY MAX", String.valueOf(config.getRetryMaxTimes()));
                        row.put("RETRY QUEUES", String.valueOf(config.getRetryQueueNums()));
                        row.put("ORDERLY", String.valueOf(config.isConsumeMessageOrderly()));
                    } else {
                        row.put("CONSUME MODE", "Unknown");
                        row.put("RETRY MAX", "-");
                        row.put("RETRY QUEUES", "-");
                        row.put("ORDERLY", "-");
                    }
                    rows.add(row);
                }

                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
                System.out.println();
                System.out.println("Total: " + allGroups.size() + " consumer group(s)");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to list consumer groups - " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "describe", description = "Describe a consumer group in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                MQAdminExt mqAdminExt = admin.getMqAdminExt();

                // Find the subscription group config from brokers
                SubscriptionGroupConfig foundConfig = admin.examineSubscriptionGroupConfig(groupName);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("Group Name", groupName);

                if (foundConfig != null) {
                    String consumeMode;
                    if (!foundConfig.isConsumeBroadcastEnable() && foundConfig.isConsumeEnable()) {
                        consumeMode = "Clustering";
                    } else if (foundConfig.isConsumeBroadcastEnable()) {
                        consumeMode = "Broadcasting";
                    } else {
                        consumeMode = "Disabled";
                    }
                    result.put("Consume Enable", String.valueOf(foundConfig.isConsumeEnable()));
                    result.put("Consume Mode", consumeMode);
                    result.put("Consume From Min", String.valueOf(foundConfig.isConsumeFromMinEnable()));
                    result.put("Consume Broadcast Enable", String.valueOf(foundConfig.isConsumeBroadcastEnable()));
                    result.put("Consume Orderly", String.valueOf(foundConfig.isConsumeMessageOrderly()));
                    result.put("Retry Max Times", String.valueOf(foundConfig.getRetryMaxTimes()));
                    result.put("Retry Queue Nums", String.valueOf(foundConfig.getRetryQueueNums()));
                    result.put("Retry Topic", "%RETRY%" + groupName);
                    result.put("Consume Timeout (min)", String.valueOf(foundConfig.getConsumeTimeoutMinute()));
                } else {
                    result.put("Status", "NOT FOUND");
                }

                // Get consumer connection info
                try {
                    ConsumerConnection connInfo = mqAdminExt.examineConsumerConnectionInfo(groupName);
                    if (connInfo != null) {
                        Set<Connection> connections = connInfo.getConnectionSet();
                        result.put("Connected Clients", String.valueOf(connections != null ? connections.size() : 0));
                        if (connInfo.getConsumeType() != null) {
                            result.put("Consume Type", connInfo.getConsumeType().name());
                        }
                        if (connInfo.getMessageModel() != null) {
                            result.put("Message Model", connInfo.getMessageModel().name());
                        }
                        if (connInfo.getConsumeFromWhere() != null) {
                            result.put("Consume From", connInfo.getConsumeFromWhere().name());
                        }
                        if (connInfo.getSubscriptionTable() != null && !connInfo.getSubscriptionTable().isEmpty()) {
                            result.put("Subscribed Topics", String.join(", ", connInfo.getSubscriptionTable().keySet()));
                        }
                        // List client IDs
                        if (connections != null && !connections.isEmpty()) {
                            StringBuilder clientIds = new StringBuilder();
                            int count = 0;
                            for (Connection conn : connections) {
                                if (count > 0) clientIds.append(", ");
                                clientIds.append(conn.getClientId());
                                clientIds.append("@").append(conn.getClientAddr());
                                count++;
                                if (count >= 10) {
                                    clientIds.append(" ... (").append(connections.size() - 10).append(" more)");
                                    break;
                                }
                            }
                            result.put("Client List", clientIds.toString());
                        }
                    }
                } catch (Exception e) {
                    result.put("Connected Clients", "0 (no active consumers or error: " + e.getMessage() + ")");
                }

                System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to describe consumer group '" + groupName + "' - " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a consumer group (L2 - default dry-run)")
    static class CreateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Option(names = {"--consume-broadcast"}, description = "Use broadcasting mode instead of clustering")
        boolean consumeBroadcast;

        @Option(names = {"--retry-max"}, description = "Maximum retry times", defaultValue = "16")
        int retryMax;

        @Option(names = {"--consume-from-min"}, description = "Enable consume from minimum offset", defaultValue = "true")
        boolean consumeFromMin;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        GroupCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes()) {
                // Execute the create operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    SubscriptionGroupConfig config = new SubscriptionGroupConfig();
                    config.setGroupName(groupName);
                    config.setConsumeEnable(true);
                    config.setConsumeFromMinEnable(consumeFromMin);
                    config.setConsumeBroadcastEnable(consumeBroadcast);
                    config.setRetryMaxTimes(retryMax);

                    int brokerCount = admin.createConsumerGroupOnAllBrokers(config);

                    String clusterName = admin.getClusterName();
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "-",
                            "create consumer group " + groupName,
                            "SUCCESS",
                            System.getProperty("user.name"));

                    System.out.println("Consumer group '" + groupName + "' created successfully on " + brokerCount + " broker(s).");
                    System.out.println("  Consume mode: " + (consumeBroadcast ? "Broadcasting" : "Clustering"));
                    System.out.println("  Retry max times: " + retryMax);
                    System.out.println("  Consume from min: " + consumeFromMin);
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to create consumer group '" + groupName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "create consumer group " + groupName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Dry-run preview
                Map<String, Object> changeDetails = new LinkedHashMap<>();
                changeDetails.put("group", groupName);
                changeDetails.put("consumeMode", consumeBroadcast ? "Broadcasting" : "Clustering");
                changeDetails.put("retryMaxTimes", retryMax);
                changeDetails.put("consumeFromMin", consumeFromMin);
                changeDetails.put("cluster", "(current context)");

                DryRunResult dryRun = DryRunResult.builder()
                        .operation("create consumer group " + groupName)
                        .willExecute(true)
                        .affectedResources(Arrays.asList("Consumer Group: " + groupName))
                        .changeDetails(changeDetails)
                        .estimatedDuration("< 1 second")
                        .warnings(Arrays.asList(
                                "Group creation is irreversible without manual deletion.",
                                "Ensure the group name follows your organization's naming convention."))
                        .build();
                System.out.println(dryRun.toDisplay());
                System.out.println("Run with --yes to confirm and execute.");
                return 0;
            }
        }
    }

    @Command(name = "update", description = "Update consumer group configuration (L2 - default dry-run)")
    static class UpdateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Option(names = {"--consume-broadcast"}, description = "Enable broadcasting mode")
        boolean consumeBroadcast;

        @Option(names = {"--retry-max"}, description = "Maximum retry times")
        Integer retryMax;

        @Option(names = {"--consume-from-min"}, description = "Enable consume from minimum offset")
        Boolean consumeFromMin;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        GroupCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes()) {
                // Execute the update operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    // Fetch existing config from a broker
                    SubscriptionGroupConfig existingConfig = admin.examineSubscriptionGroupConfig(groupName);
                    if (existingConfig == null) {
                        System.err.println("Error: Consumer group '" + groupName + "' not found on any broker.");
                        return 1;
                    }

                    // Apply updates
                    if (retryMax != null) {
                        existingConfig.setRetryMaxTimes(retryMax);
                    }
                    if (consumeFromMin != null) {
                        existingConfig.setConsumeFromMinEnable(consumeFromMin);
                    }
                    existingConfig.setConsumeBroadcastEnable(consumeBroadcast);

                    int brokerCount = admin.createConsumerGroupOnAllBrokers(existingConfig);

                    String clusterName = admin.getClusterName();
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "-",
                            "update consumer group " + groupName,
                            "SUCCESS",
                            System.getProperty("user.name"));

                    System.out.println("Consumer group '" + groupName + "' updated successfully on " + brokerCount + " broker(s).");
                    if (retryMax != null) {
                        System.out.println("  Retry max times: " + retryMax);
                    }
                    System.out.println("  Consume mode: " + (consumeBroadcast ? "Broadcasting" : "Clustering"));
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to update consumer group '" + groupName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "update consumer group " + groupName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Dry-run preview
                Map<String, Object> changeDetails = new LinkedHashMap<>();
                changeDetails.put("group", groupName);
                changeDetails.put("consumeMode", consumeBroadcast ? "Broadcasting" : "Clustering");
                if (retryMax != null) {
                    changeDetails.put("retryMaxTimes", retryMax);
                }
                if (consumeFromMin != null) {
                    changeDetails.put("consumeFromMin", consumeFromMin);
                }

                DryRunResult dryRun = DryRunResult.builder()
                        .operation("update consumer group " + groupName)
                        .willExecute(true)
                        .affectedResources(Arrays.asList("Consumer Group: " + groupName))
                        .changeDetails(changeDetails)
                        .estimatedDuration("< 1 second")
                        .warnings(Arrays.asList(
                                "Changing consume mode may disrupt in-flight message delivery."))
                        .build();
                System.out.println(dryRun.toDisplay());
                System.out.println("Run with --yes to confirm and execute.");
                return 0;
            }
        }
    }

    @Command(name = "reset-offset", description = "Reset consumer offset (L2 - default dry-run)")
    static class ResetOffsetCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Parameters(index = "1", description = "Topic name")
        String topicName;

        @Option(names = {"--timestamp"}, description = "Reset to timestamp (format: yyyy-MM-dd HH:mm:ss)")
        String timestamp;

        @Option(names = {"--by-interval"}, description = "Reset to N hours ago (e.g., 24)")
        Long intervalHours;

        @Option(names = {"--force"}, description = "Force reset even if consumers are online")
        boolean forceReset;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        GroupCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            // Determine target timestamp
            long targetTimestamp;
            String offsetTarget;
            if (timestamp != null) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    targetTimestamp = sdf.parse(timestamp).getTime();
                    offsetTarget = "timestamp " + timestamp;
                } catch (Exception e) {
                    System.err.println("Error: Invalid timestamp format. Use yyyy-MM-dd HH:mm:ss");
                    return 1;
                }
            } else if (intervalHours != null) {
                targetTimestamp = System.currentTimeMillis() - (intervalHours * 3600 * 1000);
                offsetTarget = intervalHours + " hours ago";
            } else {
                targetTimestamp = System.currentTimeMillis();
                offsetTarget = "current time";
            }

            if (root != null && root.isYes()) {
                // Execute the reset-offset operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    MQAdminExt mqAdminExt = admin.getMqAdminExt();

                    Map<?, ?> resetResult = mqAdminExt.resetOffsetByTimestamp(topicName, groupName, targetTimestamp, forceReset);

                    String clusterName = admin.getClusterName();
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "-",
                            "reset-offset " + groupName + " / " + topicName + " to " + offsetTarget,
                            "SUCCESS",
                            System.getProperty("user.name"));

                    System.out.println("Consumer offset reset successfully.");
                    System.out.println("  Group: " + groupName);
                    System.out.println("  Topic: " + topicName);
                    System.out.println("  Reset to: " + offsetTarget);
                    System.out.println("  Affected queues: " + (resetResult != null ? resetResult.size() : 0));
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to reset offset for '" + groupName + "' / '" + topicName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "reset-offset " + groupName + " / " + topicName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Dry-run preview
                Map<String, Object> changeDetails = new LinkedHashMap<>();
                changeDetails.put("group", groupName);
                changeDetails.put("topic", topicName);
                changeDetails.put("resetTo", offsetTarget);
                String targetDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(targetTimestamp));
                changeDetails.put("targetTimestamp", targetDate);
                changeDetails.put("force", forceReset);
                changeDetails.put("cluster", "(current context)");

                DryRunResult dryRun = DryRunResult.builder()
                        .operation("reset-offset for " + groupName + " / " + topicName)
                        .willExecute(true)
                        .affectedResources(Arrays.asList(
                                "Consumer Group: " + groupName,
                                "Topic: " + topicName + " (all queues)"))
                        .changeDetails(changeDetails)
                        .estimatedDuration("< 2 seconds")
                        .warnings(Arrays.asList(
                                "Resetting offset will cause message re-delivery.",
                                "Consumers in this group will replay messages from " + offsetTarget + "."))
                        .build();
                System.out.println(dryRun.toDisplay());
                System.out.println("Run with --yes to confirm and execute.");
                return 0;
            }
        }
    }

    @Command(name = "delete", description = "Delete a consumer group (L3 - requires --yes --force)")
    static class DeleteCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        GroupCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes() && root.isForce()) {
                // Execute the delete operation
                try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                    int brokerCount = admin.deleteConsumerGroupFromAllBrokers(groupName);

                    String clusterName = admin.getClusterName();
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "-",
                            "delete consumer group " + groupName,
                            "SUCCESS",
                            System.getProperty("user.name"));

                    System.out.println("Consumer group '" + groupName + "' deleted successfully from " + brokerCount + " broker(s).");
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to delete consumer group '" + groupName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            cluster != null ? cluster : "-",
                            "delete consumer group " + groupName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name"));
                    return 1;
                }
            } else {
                // Missing required flags
                System.err.println("ERROR [ERR_L3_BLOCKED]: Deleting consumer group '" + groupName
                        + "' is a dangerous operation (L3).");
                System.err.println("This will permanently remove the group and ALL its consumer offsets.");
                System.err.println("Affected: " + groupName + " (all subscription and offset data)");
                if (root == null || !root.isYes()) {
                    System.err.println("HINT: Add --yes to confirm execution.");
                }
                if (root == null || !root.isForce()) {
                    System.err.println("HINT: Add --force to acknowledge this is a destructive L3 operation.");
                }
                System.err.println("HINT: Use --yes --force to proceed, or manage via the Web Console at /consumer");
                return 1;
            }
        }
    }
}