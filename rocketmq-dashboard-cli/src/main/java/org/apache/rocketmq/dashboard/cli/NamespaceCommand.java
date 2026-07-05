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

import java.util.Arrays;
import java.util.concurrent.Callable;
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "namespace", description = "Namespace management (V5+)",
        subcommands = {NamespaceCommand.ListCmd.class, NamespaceCommand.CreateCmd.class,
                NamespaceCommand.DeleteCmd.class})
public class NamespaceCommand {

    @Command(name = "list", description = "List all namespaces")
    static class ListCmd implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            System.out.println("NAMESPACE\n--------");
            System.out.println("(requires connected 5.0 Proxy cluster)");
            System.out.println("Use 'rmqctl namespace list --cluster <name>' with a V5 cluster context.");
            return 0;
        }
    }

    @Command(name = "create", description = "Create a new namespace (L2 - default dry-run)")
    static class CreateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Namespace name")
        String namespace;

        @ParentCommand
        NamespaceCommand parent;

        @Override
        public Integer call() throws Exception {
            DryRunResult dryRun = DryRunResult.builder()
                    .operation("create namespace " + namespace)
                    .willExecute(true)
                    .affectedResources(Arrays.asList("Namespace: " + namespace))
                    .estimatedDuration("< 1 second")
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete a namespace (L3 - requires --yes --force)")
    static class DeleteCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Namespace name")
        String namespace;

        @ParentCommand
        NamespaceCommand parent;

        @Override
        public Integer call() throws Exception {
            System.err.println("ERROR [ERR_L3_BLOCKED]: Deleting namespace '" + namespace
                    + "' is a dangerous operation (L3).");
            System.err.println("HINT: Use --yes --force to proceed, or manage via the Web Console at /namespace");
            return 1;
        }
    }
}
