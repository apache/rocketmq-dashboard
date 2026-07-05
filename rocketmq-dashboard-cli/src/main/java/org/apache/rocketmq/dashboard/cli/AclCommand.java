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

@Command(name = "acl", description = "ACL management commands",
        subcommands = {AclCommand.ListCmd.class, AclCommand.CreateCmd.class,
                AclCommand.UpdateCmd.class, AclCommand.DeleteCmd.class})
public class AclCommand {

    @Command(name = "list", description = "List all ACL entries (L1)")
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
            row1.put("USER", "rocketmq");
            row1.put("TOPIC PERM", "DENY");
            row1.put("GROUP PERM", "DENY");
            row1.put("CLUSTER PERM", "DENY");
            row1.put("SOURCE IP", "192.168.*");
            rows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("USER", "admin");
            row2.put("TOPIC PERM", "PUB|SUB");
            row2.put("GROUP PERM", "SUB");
            row2.put("CLUSTER PERM", "DENY");
            row2.put("SOURCE IP", "*");
            rows.add(row2);

            Map<String, Object> row3 = new LinkedHashMap<>();
            row3.put("USER", "order-service");
            row3.put("TOPIC PERM", "PUB|SUB");
            row3.put("GROUP PERM", "SUB");
            row3.put("CLUSTER PERM", "DENY");
            row3.put("SOURCE IP", "10.0.1.*");
            rows.add(row3);

            System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
            System.out.println();
            System.out.println("Note: This is sample data. Connect to a live cluster for real ACL listing.");
            return 0;
        }
    }

    @Command(name = "create", description = "Create an ACL entry (L2 - default dry-run)")
    static class CreateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "User name (AccessKey)")
        String username;

        @Option(names = {"--topic-perm"}, description = "Topic permission: DENY, PUB, SUB, PUB|SUB")
        String topicPerm;

        @Option(names = {"--group-perm"}, description = "Consumer group permission: DENY, SUB")
        String groupPerm;

        @Option(names = {"--source-ip"}, description = "Source IP CIDR or pattern", defaultValue = "*")
        String sourceIp;

        @ParentCommand
        AclCommand parent;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("accessKey", username);
            changeDetails.put("topicPerm", topicPerm != null ? topicPerm : "(not set)");
            changeDetails.put("groupPerm", groupPerm != null ? groupPerm : "(not set)");
            changeDetails.put("sourceIp", sourceIp);

            DryRunResult dryRun = DryRunResult.builder()
                    .operation("create ACL entry for " + username)
                    .willExecute(true)
                    .affectedResources(Arrays.asList("ACL User: " + username))
                    .changeDetails(changeDetails)
                    .estimatedDuration("< 1 second")
                    .warnings(Arrays.asList(
                            "ACL changes take effect immediately on the connected cluster.",
                            "Misconfigured ACLs may block legitimate clients."))
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }
    }

    @Command(name = "update", description = "Update an ACL entry (L2 - default dry-run)")
    static class UpdateCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "User name (AccessKey)")
        String username;

        @Option(names = {"--topic-perm"}, description = "Topic permission: DENY, PUB, SUB, PUB|SUB")
        String topicPerm;

        @Option(names = {"--group-perm"}, description = "Consumer group permission: DENY, SUB")
        String groupPerm;

        @Option(names = {"--source-ip"}, description = "Source IP CIDR or pattern")
        String sourceIp;

        @ParentCommand
        AclCommand parent;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("accessKey", username);
            if (topicPerm != null) {
                changeDetails.put("topicPerm", topicPerm);
            }
            if (groupPerm != null) {
                changeDetails.put("groupPerm", groupPerm);
            }
            if (sourceIp != null) {
                changeDetails.put("sourceIp", sourceIp);
            }

            DryRunResult dryRun = DryRunResult.builder()
                    .operation("update ACL entry for " + username)
                    .willExecute(true)
                    .affectedResources(Arrays.asList("ACL User: " + username))
                    .changeDetails(changeDetails)
                    .estimatedDuration("< 1 second")
                    .warnings(Arrays.asList(
                            "ACL changes take effect immediately on the connected cluster.",
                            "Verify current ACL settings with 'rmqctl acl list' before updating."))
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete an ACL entry (L3 - requires --yes --force)")
    static class DeleteCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "User name (AccessKey)")
        String username;

        @ParentCommand
        AclCommand parent;

        @Override
        public Integer call() throws Exception {
            System.err.println("ERROR [ERR_L3_BLOCKED]: Deleting ACL entry for '" + username
                    + "' is a dangerous operation (L3).");
            System.err.println("This will revoke all access for user '" + username
                    + "' and may cause service disruption.");
            System.err.println("Affected: " + username + " (all topic and group permissions)");
            System.err.println("HINT: Use --yes --force to proceed, or manage via the Web Console at /acl");
            return 1;
        }
    }
}
