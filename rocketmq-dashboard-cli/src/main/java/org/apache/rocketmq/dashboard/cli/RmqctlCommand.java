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

import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "rmqctl",
        description = "RocketMQ Control Plane CLI - kubectl-style cluster management",
        mixinStandardHelpOptions = true,
        version = "rmqctl 2.1.1",
        subcommands = {
                ConfigCommand.class,
                ClusterCommand.class,
                TopicCommand.class,
                GroupCommand.class,
                MessageCommand.class,
                ClientCommand.class,
                AclCommand.class,
                BrokerCommand.class,
                MetricsCommand.class,
                NamespaceCommand.class,
                ExplainCommand.class,
                GenerateCompletion.class
        })

/** Picocli entry point for the rmqctl CLI tool. Provides global options (--cluster, --output, --dry-run, --yes, --force) and registers all resource subcommands. */
public class RmqctlCommand implements Callable<Integer> {

    @Option(names = {"--cluster"}, description = "Target cluster name (overrides current context)")
    String clusterOverride;

    @Option(names = {"--output"}, description = "Output format: table, json, yaml", defaultValue = "table")
    String outputFormat = "table";

    @Option(names = {"--dry-run"}, description = "Preview changes without executing")
    boolean dryRun;

    @Option(names = {"--yes"}, description = "Skip confirmation prompts")
    boolean yes;

    @Option(names = {"--force"}, description = "Force execution of dangerous operations (required for L3)")
    boolean force;

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public OutputFormatter.Format getOutputFormat() {
        switch (outputFormat.toLowerCase()) {
            case "json":
                return OutputFormatter.Format.JSON;
            case "yaml":
                return OutputFormatter.Format.YAML;
            default:
                return OutputFormatter.Format.TABLE;
        }
    }

    public String getCluster() {
        return clusterOverride;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isYes() {
        return yes;
    }

    public boolean isForce() {
        return force;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RmqctlCommand()).execute(args);
        System.exit(exitCode);
    }
}
