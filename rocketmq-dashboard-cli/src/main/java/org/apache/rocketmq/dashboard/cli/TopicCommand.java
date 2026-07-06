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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.dashboard.cli.security.AuditLogger;
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "topic", description = "Topic management commands",
        subcommands = {TopicCommand.ListCmd.class, TopicCommand.DescribeCmd.class,
                TopicCommand.CreateCmd.class, TopicCommand.UpdateCmd.class,
                TopicCommand.DeleteCmd.class})

/** CLI commands for topic management: list, describe, create (L2), update (L2), delete (L3). */
public class TopicCommand {

    @Command(name = "list", description = "List all topics (L1)")
    static class ListCmd implements Callable<Integer> {
        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            try (AdminClientHelper admin = AdminClientHelper.connect(cluster, root)) {
                MQAdminExt mqAdminExt = admin.getMqAdminExt();
                ClusterInfo clusterInfo = admin.getClusterInfo();

                Set<String> topicNames = mqAdminExt.fetchAllTopicList().getTopicList();
                if (topicNames == null || topicNames.isEmpty()) {
                    System.out.println("No topics found in the cluster.");
                    return 0;
                }

                List<String> sortedTopics = topicNames.stream().sorted().collect(Collectors.toList());

                // Build broker name list for display
                List<String> brokerNames = admin.getBrokerNames();
                String brokerSummary = brokerNames.isEmpty() ? "-" : String.join(", ", brokerNames);

                List<Map<String, Object>> rows = new ArrayList<>();
                for (String topicName : sortedTopics) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("TOPIC NAME", topicName);
                    try {
                        TopicRouteData routeData = mqAdminExt.examineTopicRouteInfo(topicName);
                        if (routeData != null && routeData.getQueueDatas() != null && !routeData.getQueueDatas().isEmpty()) {
                            QueueData qd = routeData.getQueueDatas().get(0);
                            row.put("READ QUEUE", String.valueOf(qd.getReadQueueNums()));
                            row.put("WRITE QUEUE", String.valueOf(qd.getWriteQueueNums()));
                            row.put("PERM", String.valueOf(qd.getPerm()));
                        } else {
                            row.put("READ QUEUE", "-");
                            row.put("WRITE QUEUE", "-");
                            row.put("PERM", "-");
                        }
                    } catch (Exception e) {
                        row.put("READ QUEUE", "-");
                        row.put("WRITE QUEUE", "-");
                        row.put("PERM", "-");
                    }
                    row.put("BROKERS", brokerSummary);
                    rows.add(row);
                }

                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to list topics - " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "describe", description = "Describe a topic in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            try (AdminClientHelper admin = AdminClientHelper.connect(null, root)) {
                MQAdminExt mqAdminExt = admin.getMqAdminExt();

                // Get topic route info for queue distribution
                TopicRouteData routeData = mqAdminExt.examineTopicRouteInfo(topicName);

                // Get topic config from the first available broker
                TopicConfig topicConfig = admin.examineTopicConfig(topicName);
                String configBrokerName = null;
                if (topicConfig != null && routeData != null && routeData.getQueueDatas() != null && !routeData.getQueueDatas().isEmpty()) {
                    configBrokerName = routeData.getQueueDatas().get(0).getBrokerName();
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("Topic Name", topicName);

                if (topicConfig != null) {
                    result.put("Read Queue", String.valueOf(topicConfig.getReadQueueNums()));
                    result.put("Write Queue", String.valueOf(topicConfig.getWriteQueueNums()));
                    result.put("Perm", String.valueOf(topicConfig.getPerm()));
                    result.put("Order", String.valueOf(topicConfig.isOrder()));
                } else if (routeData != null && routeData.getQueueDatas() != null && !routeData.getQueueDatas().isEmpty()) {
                    QueueData qd = routeData.getQueueDatas().get(0);
                    result.put("Read Queue", String.valueOf(qd.getReadQueueNums()));
                    result.put("Write Queue", String.valueOf(qd.getWriteQueueNums()));
                    result.put("Perm", String.valueOf(qd.getPerm()));
                    result.put("Order", "-");
                } else {
                    result.put("Read Queue", "-");
                    result.put("Write Queue", "-");
                    result.put("Perm", "-");
                    result.put("Order", "-");
                }

                if (configBrokerName != null) {
                    result.put("Broker", configBrokerName);
                }

                if (routeData != null && routeData.getQueueDatas() != null && !routeData.getQueueDatas().isEmpty()) {
                    StringBuilder queueDist = new StringBuilder();
                    for (QueueData qd : routeData.getQueueDatas()) {
                        if (queueDist.length() > 0) {
                            queueDist.append(", ");
                        }
                        queueDist.append(qd.getBrokerName())
                                .append("(R:").append(qd.getReadQueueNums())
                                .append("/W:").append(qd.getWriteQueueNums())
                                .append("/P:").append(qd.getPerm()).append(")");
                    }
                    result.put("Queue Distribution", queueDist.toString());
                }

                System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to describe topic '" + topicName + "' - " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a new topic (L2 - default dry-run)")
    static class CreateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Option(names = {"--read-queue"}, description = "Number of read queues", defaultValue = "8")
        int readQueue;

        @Option(names = {"--write-queue"}, description = "Number of write queues", defaultValue = "8")
        int writeQueue;

        @Option(names = {"--perm"}, description = "Permission: 2=W, 4=R, 6=RW", defaultValue = "6")
        int perm;

        @ParentCommand
        TopicCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes()) {
                // Execute the create operation
                try (AdminClientHelper admin = AdminClientHelper.connect(null, root)) {
                    TopicConfig topicConfig = new TopicConfig(topicName);
                    topicConfig.setReadQueueNums(readQueue);
                    topicConfig.setWriteQueueNums(writeQueue);
                    topicConfig.setPerm(perm);

                    int brokerCount = admin.createTopicOnAllBrokers(topicConfig);

                    String clusterName = admin.getClusterName();
                    System.out.println("Topic '" + topicName + "' created successfully on " + brokerCount + " broker(s).");
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "(default)",
                            "topic create " + topicName,
                            "SUCCESS",
                            System.getProperty("user.name", "unknown"));
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to create topic '" + topicName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            root.getCluster() != null ? root.getCluster() : "(default)",
                            "topic create " + topicName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name", "unknown"));
                    return 1;
                }
            }

            // Dry-run preview
            DryRunResult dryRun = DryRunResult.builder()
                    .operation("create topic " + topicName)
                    .willExecute(true)
                    .affectedResources(Arrays.asList(
                            "Topic: " + topicName + " (" + readQueue + "R/" + writeQueue + "W, perm=" + perm + ")"))
                    .estimatedDuration("< 1 second")
                    .changeDetails(buildCreateChangeDetails())
                    .warnings(Arrays.asList(
                            "Topic creation is irreversible without manual deletion.",
                            "Ensure the topic name follows your organization's naming convention."))
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }

        private Map<String, Object> buildCreateChangeDetails() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("topic", topicName);
            details.put("readQueueNums", readQueue);
            details.put("writeQueueNums", writeQueue);
            details.put("perm", perm);
            details.put("cluster", "(current context)");
            return details;
        }
    }

    @Command(name = "update", description = "Update topic configuration (L2 - default dry-run)")
    static class UpdateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Option(names = {"--read-queue"}, description = "Number of read queues")
        Integer readQueue;

        @Option(names = {"--write-queue"}, description = "Number of write queues")
        Integer writeQueue;

        @Option(names = {"--perm"}, description = "Permission: 2=W, 4=R, 6=RW")
        Integer perm;

        @ParentCommand
        TopicCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes()) {
                // Execute the update operation
                try (AdminClientHelper admin = AdminClientHelper.connect(null, root)) {
                    // Get existing config from the first available broker
                    TopicConfig topicConfig = admin.examineTopicConfig(topicName);
                    if (topicConfig == null) {
                        topicConfig = new TopicConfig(topicName);
                    }

                    // Apply updates
                    if (readQueue != null) {
                        topicConfig.setReadQueueNums(readQueue);
                    }
                    if (writeQueue != null) {
                        topicConfig.setWriteQueueNums(writeQueue);
                    }
                    if (perm != null) {
                        topicConfig.setPerm(perm);
                    }

                    int brokerCount = admin.createTopicOnAllBrokers(topicConfig);

                    String clusterName = admin.getClusterName();
                    System.out.println("Topic '" + topicName + "' updated successfully on " + brokerCount + " broker(s).");
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "(default)",
                            "topic update " + topicName,
                            "SUCCESS",
                            System.getProperty("user.name", "unknown"));
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to update topic '" + topicName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            root.getCluster() != null ? root.getCluster() : "(default)",
                            "topic update " + topicName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name", "unknown"));
                    return 1;
                }
            }

            // Dry-run preview
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("topic", topicName);
            if (readQueue != null) {
                changeDetails.put("readQueueNums", readQueue);
            }
            if (writeQueue != null) {
                changeDetails.put("writeQueueNums", writeQueue);
            }
            if (perm != null) {
                changeDetails.put("perm", perm);
            }

            DryRunResult dryRun = DryRunResult.builder()
                    .operation("update topic " + topicName)
                    .willExecute(true)
                    .affectedResources(Arrays.asList("Topic: " + topicName))
                    .changeDetails(changeDetails)
                    .estimatedDuration("< 1 second")
                    .warnings(Arrays.asList(
                            "Changing queue counts may cause uneven distribution.",
                            "Consider using 'rmqctl topic describe " + topicName + "' to review current config first."))
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete a topic (L3 - requires --yes --force)")
    static class DeleteCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @ParentCommand
        TopicCommand parent;

        @ParentCommand
        RmqctlCommand root;

        @Override
        public Integer call() throws Exception {
            if (root != null && root.isYes() && root.isForce()) {
                // Execute the delete operation
                try (AdminClientHelper admin = AdminClientHelper.connect(null, root)) {
                    admin.deleteTopicFromCluster(topicName);

                    String clusterName = admin.getClusterName();
                    System.out.println("Topic '" + topicName + "' deleted successfully.");
                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "(default)",
                            "topic delete " + topicName,
                            "SUCCESS",
                            System.getProperty("user.name", "unknown"));
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to delete topic '" + topicName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            root.getCluster() != null ? root.getCluster() : "(default)",
                            "topic delete " + topicName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name", "unknown"));
                    return 1;
                }
            }

            // Flags missing - show error with hint
            System.err.println("ERROR [ERR_L3_BLOCKED]: Deleting topic '" + topicName
                    + "' is a dangerous operation (L3).");
            System.err.println("This will permanently remove the topic and ALL its messages.");
            System.err.println("Affected: " + topicName + " (all queues and consumer offsets)");
            System.err.println("HINT: Use --yes --force to proceed, or manage via the Web Console at /topic");
            return 1;
        }
    }
}