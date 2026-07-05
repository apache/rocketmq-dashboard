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
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.context.CliConfig;
import org.apache.rocketmq.dashboard.cli.context.CliContext;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "cluster", description = "Cluster management operations",
        subcommands = {ClusterCommand.ListClusters.class, ClusterCommand.DescribeCluster.class})
public class ClusterCommand {

    @Command(name = "list", description = "List all clusters and their health status")
    static class ListClusters implements Callable<Integer> {
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
                row.put("TYPE", cluster.getClusterType());
                row.put("NAMESRV", cluster.getNamesrvAddr());
                rows.add(row);
            }
            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            return 0;
        }
    }

    @Command(name = "describe", description = "Describe cluster topology and capabilities")
    static class DescribeCluster implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name", arity = "0..1")
        String clusterName;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            String name = clusterName != null ? clusterName : ctx.getCurrentContext();
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
            result.put("cluster name", name);
            result.put("type", cluster.getClusterType());
            result.put("namesrv addr", cluster.getNamesrvAddr());
            result.put("proxy addr", cluster.getProxyAddr() != null && !cluster.getProxyAddr().isEmpty()
                    ? cluster.getProxyAddr() : "N/A");
            System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
            return 0;
        }
    }
}
