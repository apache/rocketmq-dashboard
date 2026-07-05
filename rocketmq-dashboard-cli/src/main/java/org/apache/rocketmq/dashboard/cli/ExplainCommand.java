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

import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Explains a resource or command, showing available verbs, parameters, and usage examples.
 */
@Command(name = "explain", description = "Show resource field descriptions, available actions, and usage examples")
public class ExplainCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Resource name (e.g., topic, group, cluster)")
    String resource;

    @Override
    public Integer call() {
        List<ToolDefinition> tools = ToolRegistry.getInstance().getToolsByResource(resource);
        if (tools.isEmpty()) {
            System.out.println("Unknown resource: " + resource);
            System.out.println("Available resources: topic, group, cluster, namespace, message, client, acl, broker, metrics");
            return 1;
        }

        System.out.println("RESOURCE: " + resource.toUpperCase());
        System.out.println("=".repeat(60));

        for (ToolDefinition tool : tools) {
            System.out.printf("%nVERB: %s [%s]%n", tool.getVerb(), tool.getRiskLevel().getLabel());
            System.out.println("  " + tool.getDescription());
            if (tool.getParams() != null && !tool.getParams().isEmpty()) {
                System.out.println("  Parameters:");
                for (ParamSchema param : tool.getParams()) {
                    String required = param.isRequired() ? " (required)" : " (optional)";
                    String defaultVal = param.getDefaultValue() != null ? " [default: " + param.getDefaultValue() + "]" : "";
                    System.out.printf("    --%s: %s%s%s%n",
                        param.getName(), param.getDescription(), required, defaultVal);
                }
            }
        }

        System.out.println();
        return 0;
    }
}
