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
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.CliContext;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "client", description = "Client management commands",
        subcommands = {ClientCommand.ListCmd.class, ClientCommand.DescribeCmd.class})
public class ClientCommand {

    @Command(name = "list", description = "List all connected clients (L1)")
    static class ListCmd implements Callable<Integer> {
        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @Option(names = {"--group"}, description = "Filter by consumer group")
        String group;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String clusterName = cluster != null ? cluster : ctx.getCurrentContext();
            System.out.println("Cluster: " + (clusterName != null ? clusterName : "(not set)"));
            if (group != null) {
                System.out.println("Filter Group: " + group);
            }
            System.out.println();

            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("CLIENT ID", "10.0.1.15@DEFAULT");
            row1.put("ADDRESS", "10.0.1.15:54321");
            row1.put("VERSION", "V5_2.0");
            row1.put("ROLE", "Producer");
            row1.put("CONNECTED SINCE", "2026-07-04 10:30:00");
            row1.put("TOPICS", "1");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("CLIENT ID", "10.0.1.16@DEFAULT");
            row2.put("ADDRESS", "10.0.1.16:54322");
            row2.put("VERSION", "V5_2.0");
            row2.put("ROLE", "Consumer");
            row2.put("CONNECTED SINCE", "2026-07-04 10:31:00");
            row2.put("TOPICS", "2");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("CLIENT ID", "10.0.1.17@DEFAULT");
            row3.put("ADDRESS", "10.0.1.17:54323");
            row3.put("VERSION", "V4_9.5");
            row3.put("ROLE", "Consumer");
            row3.put("CONNECTED SINCE", "2026-07-04 10:32:00");
            row3.put("TOPICS", "1");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("CLIENT ID", "10.0.1.18@DEFAULT");
            row4.put("ADDRESS", "10.0.1.18:54324");
            row4.put("VERSION", "V5_2.0");
            row4.put("ROLE", "Producer");
            row4.put("CONNECTED SINCE", "2026-07-04 10:33:00");
            row4.put("TOPICS", "3");
            rows.add(row4);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real client listing.");
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe a client in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Client ID")
        String clientId;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("Client ID", clientId);
            result.put("Address", "10.0.1.16:54322");
            result.put("Language", "JAVA");
            result.put("Version", "V5_2.0");
            result.put("Role", "Consumer");
            result.put("Connected Since", "2026-07-04 10:31:00");
            result.put("Protocol", "gRPC");
            result.put("Last Heartbeat", "2026-07-04 11:30:00");
            result.put("TLS", "Enabled");
            result.put("Subscriptions", "TOPIC_ORDER_PAY, TOPIC_ORDER_STATUS");
            result.put("Consume Group", "ORDER_CONSUMER_GROUP");
            result.put("Consume Latency", "145 (diff)");
            result.put("Thread Pool", "20 core / 40 max");

            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real client details.");
            return 0;
        }
    }
}
