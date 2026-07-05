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
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.CliContext;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "group", description = "Consumer group management commands",
        subcommands = {GroupCommand.ListCmd.class, GroupCommand.DescribeCmd.class,
                GroupCommand.CreateCmd.class, GroupCommand.UpdateCmd.class,
                GroupCommand.ResetOffsetCmd.class, GroupCommand.DeleteCmd.class})
public class GroupCommand {

    @Command(name = "list", description = "List all consumer groups (L1)")
    static class ListCmd implements Callable<Integer> {
        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String clusterName = cluster != null ? cluster : ctx.getCurrentContext();
            System.out.println("Cluster: " + (clusterName != null ? clusterName : "(not set)"));
            System.out.println();

            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("GROUP NAME", "ORDER_CONSUMER_GROUP");
            row1.put("TYPE", "Push");
            row1.put("CONSUME MODE", "Clustering");
            row1.put("STATUS", "OK");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("GROUP NAME", "GOODS_CONSUMER_GROUP");
            row2.put("TYPE", "Push");
            row2.put("CONSUME MODE", "Clustering");
            row2.put("STATUS", "OK");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("GROUP NAME", "BATCH_PROCESSOR_GROUP");
            row3.put("TYPE", "Pull");
            row3.put("CONSUME MODE", "Clustering");
            row3.put("STATUS", "WARN");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("GROUP NAME", "TEST_CONSUMER_GROUP");
            row4.put("TYPE", "Push");
            row4.put("CONSUME MODE", "Broadcasting");
            row4.put("STATUS", "OK");
            rows.add(row4);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real group listing.");
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe a consumer group in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("Group Name", groupName);
            result.put("Type", "Push");
            result.put("Consume Mode", "Clustering");
            result.put("Consume From Min", "true");
            result.put("Retry Times", "5");
            result.put("Retry Topic", "%RETRY%" + groupName);
            result.put("Topics Subscribed", "TOPIC_ORDER_PAY, TOPIC_ORDER_STATUS");
            result.put("Client Count", "3");
            result.put("Status", "OK");
            result.put("Total Diff", "145");
            result.put("Last Consume Time", "2026-07-04 11:25:33");
            result.put("Create Time", "2026-06-15 08:00:00");

            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real group details.");
            return 0;
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

        @ParentCommand
        GroupCommand parent;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("group", groupName);
            changeDetails.put("consumeMode", consumeBroadcast ? "Broadcasting" : "Clustering");
            changeDetails.put("retryMaxTimes", retryMax);
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

    @Command(name = "update", description = "Update consumer group configuration (L2 - default dry-run)")
    static class UpdateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @Option(names = {"--consume-broadcast"}, description = "Enable broadcasting mode")
        boolean consumeBroadcast;

        @Option(names = {"--retry-max"}, description = "Maximum retry times")
        Integer retryMax;

        @ParentCommand
        GroupCommand parent;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("group", groupName);
            changeDetails.put("consumeMode", consumeBroadcast ? "Broadcasting" : "Clustering");
            if (retryMax != null) {
                changeDetails.put("retryMaxTimes", retryMax);
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

        @ParentCommand
        GroupCommand parent;

        @Override
        public Integer call() throws Exception {
            String offsetTarget;
            if (timestamp != null) {
                offsetTarget = "timestamp " + timestamp;
            } else if (intervalHours != null) {
                offsetTarget = intervalHours + " hours ago";
            } else {
                offsetTarget = "latest offset";
            }

            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("group", groupName);
            changeDetails.put("topic", topicName);
            changeDetails.put("resetTo", offsetTarget);
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

    @Command(name = "delete", description = "Delete a consumer group (L3 - requires --yes --force)")
    static class DeleteCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Consumer group name")
        String groupName;

        @ParentCommand
        GroupCommand parent;

        @Override
        public Integer call() throws Exception {
            System.err.println("ERROR [ERR_L3_BLOCKED]: Deleting consumer group '" + groupName
                    + "' is a dangerous operation (L3).");
            System.err.println("This will permanently remove the group and ALL its consumer offsets.");
            System.err.println("Affected: " + groupName + " (all subscription and offset data)");
            System.err.println("HINT: Use --yes --force to proceed, or manage via the Web Console at /consumer");
            return 1;
        }
    }
}
