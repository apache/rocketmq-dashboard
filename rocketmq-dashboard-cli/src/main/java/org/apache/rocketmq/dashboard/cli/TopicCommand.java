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
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import org.apache.rocketmq.dashboard.cli.context.CliContext;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
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

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String clusterName = cluster != null ? cluster : ctx.getCurrentContext();
            System.out.println("Cluster: " + (clusterName != null ? clusterName : "(not set)"));
            System.out.println();

            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("TOPIC NAME", "TOPIC_ORDER_PAY");
            row1.put("TYPE", "Normal");
            row1.put("READ QUEUE", "8");
            row1.put("WRITE QUEUE", "8");
            row1.put("STATUS", "OK");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("TOPIC NAME", "TOPIC_ORDER_STATUS");
            row2.put("TYPE", "Normal");
            row2.put("READ QUEUE", "4");
            row2.put("WRITE QUEUE", "4");
            row2.put("STATUS", "OK");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("TOPIC NAME", "TOPIC_GOODS_CHANGE");
            row3.put("TYPE", "Normal");
            row3.put("READ QUEUE", "8");
            row3.put("WRITE QUEUE", "8");
            row3.put("STATUS", "OK");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("TOPIC NAME", "DLQ_ORDER_PAY");
            row4.put("TYPE", "DLQ");
            row4.put("READ QUEUE", "1");
            row4.put("WRITE QUEUE", "1");
            row4.put("STATUS", "WARN");
            rows.add(row4);

            Map<String, Object> row5 = new LinkedHashMap<>();
            row5.put("TOPIC NAME", "SCHEDULE_TOPIC_XXXX");
            row5.put("TYPE", "System");
            row5.put("READ QUEUE", "8");
            row5.put("WRITE QUEUE", "8");
            row5.put("STATUS", "OK");
            rows.add(row5);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real topic listing.");
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe a topic in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("Topic Name", topicName);
            result.put("Type", "Normal");
            result.put("Read Queue", "8");
            result.put("Write Queue", "8");
            result.put("Perm", "6 (R/W)");
            result.put("Status", "OK");
            result.put("Total Messages", "1,245,332");
            result.put("Total Size", "2.3 GB");
            result.put("Min Offset", "0");
            result.put("Max Offset", "1245332");
            result.put("Create Time", "2026-06-15 08:00:00");
            result.put("Last Update", "2026-07-04 10:30:00");
            result.put("Broker", "broker-a (Master)");

            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real topic details.");
            return 0;
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

        @Override
        public Integer call() throws Exception {
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

        @Override
        public Integer call() throws Exception {
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

        @Override
        public Integer call() throws Exception {
            System.err.println("ERROR [ERR_L3_BLOCKED]: Deleting topic '" + topicName
                    + "' is a dangerous operation (L3).");
            System.err.println("This will permanently remove the topic and ALL its messages.");
            System.err.println("Affected: " + topicName + " (all queues and consumer offsets)");
            System.err.println("HINT: Use --yes --force to proceed, or manage via the Web Console at /topic");
            return 1;
        }
    }
}
