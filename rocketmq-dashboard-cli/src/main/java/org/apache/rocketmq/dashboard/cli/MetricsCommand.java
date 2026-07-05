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

@Command(name = "metrics", description = "Metrics query commands",
        subcommands = {MetricsCommand.QueryCmd.class})
public class MetricsCommand {

    @Command(name = "query", description = "Query metrics for a given resource type (L1)")
    static class QueryCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Metric type: cluster, broker, topic, consumer, client, system")
        String metricType;

        @Parameters(index = "1", description = "Resource name (topic name, broker name, etc.)", arity = "0..1")
        String resourceName;

        @Option(names = {"--time-range"}, description = "Time range: 5m, 15m, 1h, 6h, 24h", defaultValue = "15m")
        String timeRange;

        @Option(names = {"--cluster"}, description = "Target cluster name")
        String cluster;

        @Override
        public Integer call() throws Exception {
            // Validate metric type
            String lowerType = metricType != null ? metricType.toLowerCase() : "";
            switch (lowerType) {
                case "cluster":
                case "broker":
                case "topic":
                case "consumer":
                case "client":
                case "system":
                    break;
                default:
                    System.err.println("ERROR: Invalid metric type '" + metricType
                            + "'. Allowed values: cluster, broker, topic, consumer, client, system");
                    return 1;
            }

            CliContext ctx = new CliContext();
            String clusterName = cluster != null ? cluster : ctx.getCurrentContext();
            System.out.println("Metric Type: " + lowerType);
            System.out.println("Resource: " + (resourceName != null ? resourceName : "(all)"));
            System.out.println("Cluster: " + (clusterName != null ? clusterName : "(not set)"));
            System.out.println("Time Range: " + timeRange);
            System.out.println();

            switch (lowerType) {
                case "cluster":
                    displayClusterMetrics();
                    break;
                case "broker":
                    displayBrokerMetrics(resourceName);
                    break;
                case "topic":
                    displayTopicMetrics(resourceName);
                    break;
                case "consumer":
                    displayConsumerMetrics(resourceName);
                    break;
                case "client":
                    displayClientMetrics(resourceName);
                    break;
                case "system":
                    displaySystemMetrics();
                    break;
                default:
                    break;
            }

            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real metrics.");
            return 0;
        }

        private void displayClusterMetrics() {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("METRIC", "In TPS");
            row1.put("VALUE", "3,200");
            row1.put("MIN", "2,800");
            row1.put("MAX", "3,500");
            row1.put("AVG", "3,200");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("METRIC", "Out TPS");
            row2.put("VALUE", "3,150");
            row2.put("MIN", "2,750");
            row2.put("MAX", "3,450");
            row2.put("AVG", "3,150");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("METRIC", "Total Messages");
            row3.put("VALUE", "12,450,000");
            row3.put("MIN", "-");
            row3.put("MAX", "-");
            row3.put("AVG", "-");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("METRIC", "Total Transferred (MB/s)");
            row4.put("VALUE", "45.2");
            row4.put("MIN", "38.0");
            row4.put("MAX", "52.1");
            row4.put("AVG", "45.0");
            rows.add(row4);

            Map<String, Object> row5 = new LinkedHashMap<>();
            row5.put("METRIC", "Active Connections");
            row5.put("VALUE", "48");
            row5.put("MIN", "42");
            row5.put("MAX", "52");
            row5.put("AVG", "47");
            rows.add(row5);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
        }

        private void displayBrokerMetrics(String brokerName) {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("BROKER", brokerName != null ? brokerName : "broker-a");
            row1.put("IN TPS", "1,500");
            row1.put("OUT TPS", "1,500");
            row1.put("DISK USE", "45%");
            row1.put("MEM USE", "75%");
            row1.put("CONNS", "24");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("BROKER", "broker-b");
            row2.put("IN TPS", "1,200");
            row2.put("OUT TPS", "1,180");
            row2.put("DISK USE", "38%");
            row2.put("MEM USE", "68%");
            row2.put("CONNS", "18");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("BROKER", "broker-c");
            row3.put("IN TPS", "500");
            row3.put("OUT TPS", "470");
            row3.put("DISK USE", "22%");
            row3.put("MEM USE", "55%");
            row3.put("CONNS", "6");
            rows.add(row3);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
        }

        private void displayTopicMetrics(String topicName) {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("TOPIC", topicName != null ? topicName : "TOPIC_ORDER_PAY");
            row1.put("IN TPS", "1,200");
            row1.put("OUT TPS", "1,180");
            row1.put("IN (MB/s)", "5.5");
            row1.put("OUT (MB/s)", "5.4");
            row1.put("TOTAL MSGS", "4,500,000");
            rows.add(row1);

            if (topicName == null) {
                Map<String, Object> row2 = new LinkedHashMap<>();
                row2.put("TOPIC", "TOPIC_ORDER_STATUS");
                row2.put("IN TPS", "800");
                row2.put("OUT TPS", "790");
                row2.put("IN (MB/s)", "3.2");
                row2.put("OUT (MB/s)", "3.1");
                row2.put("TOTAL MSGS", "3,200,000");
                rows.add(row2);

                Map<String, Object> row3 = new LinkedHashMap<>();
                row3.put("TOPIC", "TOPIC_GOODS_CHANGE");
                row3.put("IN TPS", "1,200");
                row3.put("OUT TPS", "1,180");
                row3.put("IN (MB/s)", "6.1");
                row3.put("OUT (MB/s)", "5.9");
                row3.put("TOTAL MSGS", "4,750,000");
                rows.add(row3);
            }

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
        }

        private void displayConsumerMetrics(String consumerGroup) {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("CONSUMER GROUP", consumerGroup != null ? consumerGroup : "ORDER_CONSUMER_GROUP");
            row1.put("TPM", "25,000");
            row1.put("DIFF TOTAL", "145");
            row1.put("CLIENTS", "3");
            row1.put("LATENCY (ms)", "5.2");
            rows.add(row1);

            if (consumerGroup == null) {
                Map<String, Object> row2 = new LinkedHashMap<>();
                row2.put("CONSUMER GROUP", "GOODS_CONSUMER_GROUP");
                row2.put("TPM", "18,500");
                row2.put("DIFF TOTAL", "89");
                row2.put("CLIENTS", "4");
                row2.put("LATENCY (ms)", "3.8");
                rows.add(row2);

                Map<String, Object> row3 = new LinkedHashMap<>();
                row3.put("CONSUMER GROUP", "BATCH_PROCESSOR_GROUP");
                row3.put("TPM", "5,200");
                row3.put("DIFF TOTAL", "12,450");
                row3.put("CLIENTS", "2");
                row3.put("LATENCY (ms)", "120.5");
                rows.add(row3);
            }

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
        }

        private void displayClientMetrics(String clientId) {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("CLIENT", clientId != null ? clientId : "10.0.1.15@DEFAULT");
            row1.put("PRODUCED", "12,500");
            row1.put("CONSUMED", "0");
            row1.put("ERRORS", "0");
            row1.put("RT (ms)", "1.2");
            row1.put("CONNECTED", "1h 30m");
            rows.add(row1);

            if (clientId == null) {
                Map<String, Object> row2 = new LinkedHashMap<>();
                row2.put("CLIENT", "10.0.1.16@DEFAULT");
                row2.put("PRODUCED", "0");
                row2.put("CONSUMED", "25,000");
                row2.put("ERRORS", "3");
                row2.put("RT (ms)", "5.2");
                row2.put("CONNECTED", "1h 29m");
                rows.add(row2);

                Map<String, Object> row3 = new LinkedHashMap<>();
                row3.put("CLIENT", "10.0.1.18@DEFAULT");
                row3.put("PRODUCED", "18,200");
                row3.put("CONSUMED", "0");
                row3.put("ERRORS", "1");
                row3.put("RT (ms)", "0.8");
                row3.put("CONNECTED", "1h 27m");
                rows.add(row3);
            }

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
        }

        private void displaySystemMetrics() {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("METRIC", "JVM Heap Used");
            row1.put("VALUE", "2.8 GB / 4.0 GB (70%)");
            row1.put("STATUS", "OK");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("METRIC", "GC Count (Young)");
            row2.put("VALUE", "245 / hour");
            row2.put("STATUS", "OK");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("METRIC", "GC Pause Time (Avg)");
            row3.put("VALUE", "12 ms");
            row3.put("STATUS", "OK");
            rows.add(row3);

            Map<String, Object> row4 = new LinkedHashMap<>();
            row4.put("METRIC", "Thread Count");
            row4.put("VALUE", "312 active / 500 max");
            row4.put("STATUS", "OK");
            rows.add(row4);

            Map<String, Object> row5 = new LinkedHashMap<>();
            row5.put("METRIC", "File Descriptor Count");
            row5.put("VALUE", "1,245 / 65,535 (1.9%)");
            row5.put("STATUS", "OK");
            rows.add(row5);

            Map<String, Object> row6 = new LinkedHashMap<>();
            row6.put("METRIC", "Page Cache Hit Rate");
            row6.put("VALUE", "98.5%");
            row6.put("STATUS", "OK");
            rows.add(row6);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
        }
    }
}
