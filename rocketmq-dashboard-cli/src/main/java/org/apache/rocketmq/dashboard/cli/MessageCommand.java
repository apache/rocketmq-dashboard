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

@Command(name = "message", description = "Message management commands",
        subcommands = {MessageCommand.QueryByIdCmd.class, MessageCommand.QueryByTimeCmd.class,
                MessageCommand.ResendCmd.class})

/** CLI commands for message operations: query-by-id, query-by-time, resend (L2). */
public class MessageCommand {

    @Command(name = "query-by-id", description = "Query a message by its ID (L1)")
    static class QueryByIdCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Message ID")
        String messageId;

        @Parameters(index = "1", description = "Topic name")
        String topicName;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("Message ID", messageId);
            result.put("Topic", topicName);
            result.put("Tags", "OrderPaid");
            result.put("Keys", "ORDER-20260704-001");
            result.put("Queue ID", "3");
            result.put("Offset", "125,448");
            result.put("Born Time", "2026-07-04 11:25:33.456");
            result.put("Store Time", "2026-07-04 11:25:33.458");
            result.put("Born Host", "10.0.1.15:54321");
            result.put("Store Host", "10.0.2.10:10911");
            result.put("Body Size", "256 bytes");
            result.put("Body Preview", "{\"orderId\":\"ORD-20260704-001\",\"amount\":125.50,\"status\":\"PAID\"}");

            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real message queries.");
            return 0;
        }
    }

    @Command(name = "query-by-time", description = "Query messages by time range (L1)")
    static class QueryByTimeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Option(names = {"--begin"}, description = "Begin timestamp (format: yyyy-MM-dd HH:mm:ss)")
        String beginT;

        @Option(names = {"--end"}, description = "End timestamp (format: yyyy-MM-dd HH:mm:ss)")
        String endT;

        @Option(names = {"--max"}, description = "Maximum number of messages", defaultValue = "10")
        int maxNum;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String cluster = ctx.getCurrentContext();
            System.out.println("Cluster: " + (cluster != null ? cluster : "(not set)"));
            System.out.println("Topic: " + topicName);
            System.out.println("Time Range: " + (beginT != null ? beginT : "30 min ago")
                    + " ~ " + (endT != null ? endT : "now"));
            System.out.println("Max Results: " + maxNum);
            System.out.println();

            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("MESSAGE ID", "0A0001153C2D18B4AEE266C1C9A80003");
            row1.put("QUEUE", "3");
            row1.put("OFFSET", "125,448");
            row1.put("BORN TIME", "2026-07-04 11:25:33");
            row1.put("STORE TIME", "2026-07-04 11:25:33");
            row1.put("SIZE", "256 B");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("MESSAGE ID", "0A0001153C2D18B4AEE266C1C9A80004");
            row2.put("QUEUE", "5");
            row2.put("OFFSET", "89,224");
            row2.put("BORN TIME", "2026-07-04 11:25:34");
            row2.put("STORE TIME", "2026-07-04 11:25:34");
            row2.put("SIZE", "512 B");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("MESSAGE ID", "0A0001153C2D18B4AEE266C1C9A80005");
            row3.put("QUEUE", "1");
            row3.put("OFFSET", "201,033");
            row3.put("BORN TIME", "2026-07-04 11:25:35");
            row3.put("STORE TIME", "2026-07-04 11:25:35");
            row3.put("SIZE", "128 B");
            rows.add(row3);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real message queries.");
            return 0;
        }
    }

    @Command(name = "resend", description = "Resend a message to a consumer group (L2 - default dry-run)")
    static class ResendCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Message ID")
        String messageId;

        @Parameters(index = "1", description = "Target consumer group")
        String groupName;

        @Parameters(index = "2", description = "Topic name")
        String topicName;

        @ParentCommand
        MessageCommand parent;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("messageId", messageId);
            changeDetails.put("targetGroup", groupName);
            changeDetails.put("topic", topicName);

            DryRunResult dryRun = DryRunResult.builder()
                    .operation("resend message " + messageId + " to group " + groupName)
                    .willExecute(true)
                    .affectedResources(Arrays.asList(
                            "Message: " + messageId,
                            "Consumer Group: " + groupName))
                    .changeDetails(changeDetails)
                    .estimatedDuration("< 1 second")
                    .warnings(Arrays.asList(
                            "Resending messages may cause duplicate processing.",
                            "Ensure the target consumer group has idempotent handling."))
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }
    }
}
