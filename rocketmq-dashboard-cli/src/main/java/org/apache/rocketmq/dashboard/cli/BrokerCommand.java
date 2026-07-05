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

@Command(name = "broker", description = "Broker management commands",
        subcommands = {BrokerCommand.ListCmd.class, BrokerCommand.DescribeCmd.class,
                BrokerCommand.ConfigCmd.class})

/** CLI commands for broker operations: list brokers, describe broker details, view/update broker configuration. */
public class BrokerCommand {

    @Command(name = "list", description = "List all brokers in the cluster (L1)")
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
            row1.put("BROKER NAME", "broker-a");
            row1.put("ADDRESS", "10.0.2.10:10911");
            row1.put("VERSION", "V5_2.0");
            row1.put("ROLE", "Master");
            row1.put("STATUS", "ONLINE");
            row1.put("TOPICS", "12");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("BROKER NAME", "broker-a-slave");
            row2.put("ADDRESS", "10.0.2.11:10911");
            row2.put("VERSION", "V5_2.0");
            row2.put("ROLE", "Slave");
            row2.put("STATUS", "ONLINE");
            row2.put("TOPICS", "12");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("BROKER NAME", "broker-b");
            row3.put("ADDRESS", "10.0.2.20:10911");
            row3.put("VERSION", "V5_2.0");
            row3.put("ROLE", "Master");
            row3.put("STATUS", "ONLINE");
            row3.put("TOPICS", "10");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("BROKER NAME", "broker-c");
            row4.put("ADDRESS", "10.0.2.30:10911");
            row4.put("VERSION", "V5_2.0");
            row4.put("ROLE", "Master");
            row4.put("STATUS", "ONLINE");
            row4.put("TOPICS", "8");
            rows.add(row4);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real broker listing.");
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe a broker in detail (L1)")
    static class DescribeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Broker name")
        String brokerName;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("Broker Name", brokerName);
            result.put("Cluster", "DefaultCluster");
            result.put("Address", "10.0.2.10:10911");
            result.put("Version", "V5_2.0");
            result.put("Role", "Master");
            result.put("Status", "ONLINE");
            result.put("CommitLog Max Offset", "15,234,567,890");
            result.put("CommitLog Min Offset", "0");
            result.put("Topic Count", "12");
            result.put("Queue Count", "96");
            result.put("Boot Time", "2026-06-15 08:00:00");
            result.put("Uptime", "19 days 3 hours 30 minutes");
            result.put("In TPS", "1,500");
            result.put("Out TPS", "1,500");
            result.put("Producer Count", "8");
            result.put("Consumer Count", "15");
            result.put("Memory", "75% (3.0GB / 4.0GB)");
            result.put("Disk", "45% (450GB / 1.0TB)");

            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real broker details.");
            return 0;
        }
    }

    @Command(name = "config", description = "Show broker runtime configuration (L1)")
    static class ConfigCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Broker name")
        String brokerName;

        @Option(names = {"--filter"}, description = "Filter config keys (e.g., 'flush,commitlog')")
        String filter;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String cluster = ctx.getCurrentContext();
            System.out.println("Cluster: " + (cluster != null ? cluster : "(not set)"));
            System.out.println("Broker: " + brokerName);
            if (filter != null) {
                System.out.println("Filter: " + filter);
            }
            System.out.println();

            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("CONFIG KEY", "flushDiskType");
            row1.put("VALUE", "ASYNC_FLUSH");
            row1.put("DEFAULT", "ASYNC_FLUSH");
            row1.put("DYNAMIC", "true");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("CONFIG KEY", "flushCommitLogTimed");
            row2.put("VALUE", "false");
            row2.put("DEFAULT", "false");
            row2.put("DYNAMIC", "true");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("CONFIG KEY", "syncFlushTimeout");
            row3.put("VALUE", "5000");
            row3.put("DEFAULT", "5000");
            row3.put("DYNAMIC", "false");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("CONFIG KEY", "brokerRole");
            row4.put("VALUE", "SYNC_MASTER");
            row4.put("DEFAULT", "ASYNC_MASTER");
            row4.put("DYNAMIC", "false");
            rows.add(row4);

            Map<String, Object> row5 = new LinkedHashMap<>();
            row5.put("CONFIG KEY", "maxMessageSize");
            row5.put("VALUE", "4194304");
            row5.put("DEFAULT", "4194304");
            row5.put("DYNAMIC", "true");
            rows.add(row5);

            Map<String, Object> row6 = new LinkedHashMap<>();
            row6.put("CONFIG KEY", "sendMessageThreadPoolNums");
            row6.put("VALUE", "8");
            row6.put("DEFAULT", "8");
            row6.put("DYNAMIC", "false");
            rows.add(row6);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real broker configuration.");
            return 0;
        }
    }
}
