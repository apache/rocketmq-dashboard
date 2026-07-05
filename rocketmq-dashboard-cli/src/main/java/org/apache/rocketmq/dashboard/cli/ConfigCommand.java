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
import org.apache.rocketmq.dashboard.cli.context.CliConfig;
import org.apache.rocketmq.dashboard.cli.context.CliContext;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "config", description = "Manage cluster contexts and configuration",
        subcommands = {
                ConfigCommand.GetContexts.class,
                ConfigCommand.UseContext.class,
                ConfigCommand.SetContext.class,
                ConfigCommand.AddCluster.class
        })
public class ConfigCommand {

    @Command(name = "get-contexts", description = "List all configured contexts")
    static class GetContexts implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            List<CliConfig.ContextEntry> contexts = ctx.getContexts();
            String current = ctx.getCurrentContext();

            List<Map<String, Object>> rows = new ArrayList<>();
            for (CliConfig.ContextEntry c : contexts) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("CURRENT", c.getName().equals(current) ? "*" : "");
                row.put("NAME", c.getName());
                row.put("CLUSTER", c.getCluster());
                row.put("USER", c.getUser() != null ? c.getUser() : "-");
                row.put("NAMESPACE", c.getNamespace() != null ? c.getNamespace() : "-");
                rows.add(row);
            }
            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            return 0;
        }
    }

    @Command(name = "use-context", description = "Switch to a different context")
    static class UseContext implements Callable<Integer> {
        @Parameters(index = "0", description = "Context name")
        String contextName;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            ctx.setCurrentContext(contextName);
            System.out.println("Switched to context \"" + contextName + "\".");
            return 0;
        }
    }

    @Command(name = "set-context", description = "Create or update a context")
    static class SetContext implements Callable<Integer> {
        @Parameters(index = "0", description = "Context name")
        String name;
        @Parameters(index = "1", description = "Cluster name")
        String cluster;
        @Parameters(index = "2", description = "User name (optional)", arity = "0..1")
        String user;
        @Parameters(index = "3", description = "Namespace (optional)", arity = "0..1")
        String namespace;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            ctx.addContext(name, cluster, user, namespace);
            System.out.println("Context \"" + name + "\" set.");
            return 0;
        }
    }

    @Command(name = "add-cluster", description = "Add a new cluster to configuration")
    static class AddCluster implements Callable<Integer> {
        @Parameters(index = "0", description = "Cluster name")
        String name;
        @Parameters(index = "1", description = "NameServer address")
        String namesrvAddr;
        @Parameters(index = "2", description = "Proxy address (optional)", arity = "0..1")
        String proxyAddr;
        @Parameters(index = "3", description = "Cluster type", arity = "0..1", defaultValue = "V4_NAMESRV")
        String clusterType;

        @Override
        public Integer call() throws Exception {
            CliContext ctx = new CliContext();
            ctx.addCluster(name, namesrvAddr, proxyAddr != null ? proxyAddr : "", clusterType);
            System.out.println("Cluster \"" + name + "\" added.");
            return 0;
        }
    }
}
