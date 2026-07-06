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

import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

public class RmqctlCommandTest {

    @Test
    public void testMainHelp() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);
        // Verifying --help does not throw
        String usage = cmd.getUsageMessage();
        Assert.assertNotNull(usage);
        Assert.assertTrue(usage.contains("rmqctl"));
    }

    @Test
    public void testGlobalOptions() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);

        CommandLine.Model.CommandSpec spec = cmd.getCommandSpec();
        Assert.assertTrue(spec.options().stream()
                .anyMatch(o -> "--output".equals(o.longestName())));
        Assert.assertTrue(spec.options().stream()
                .anyMatch(o -> "--cluster".equals(o.longestName())));
        Assert.assertTrue(spec.options().stream()
                .anyMatch(o -> "--dry-run".equals(o.longestName())));
        Assert.assertTrue(spec.options().stream()
                .anyMatch(o -> "--yes".equals(o.longestName())));
        Assert.assertTrue(spec.options().stream()
                .anyMatch(o -> "--force".equals(o.longestName())));
    }

    @Test
    public void testDefaultOutputFormat() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertEquals(OutputFormatter.Format.TABLE, command.getOutputFormat());
    }

    @Test
    public void testJsonOutputFormat() {
        RmqctlCommand command = new RmqctlCommand();
        command.outputFormat = "json";
        Assert.assertEquals(OutputFormatter.Format.JSON, command.getOutputFormat());
    }

    @Test
    public void testYamlOutputFormat() {
        RmqctlCommand command = new RmqctlCommand();
        command.outputFormat = "yaml";
        Assert.assertEquals(OutputFormatter.Format.YAML, command.getOutputFormat());
    }

    @Test
    public void testInvalidOutputFormatDefaultsToTable() {
        RmqctlCommand command = new RmqctlCommand();
        command.outputFormat = "invalid";
        Assert.assertEquals(OutputFormatter.Format.TABLE, command.getOutputFormat());
    }

    @Test
    public void testSubcommandsExist() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);

        Assert.assertNotNull(cmd.getSubcommands().get("config"));
        Assert.assertNotNull(cmd.getSubcommands().get("topic"));
        Assert.assertNotNull(cmd.getSubcommands().get("group"));
        Assert.assertNotNull(cmd.getSubcommands().get("cluster"));
        Assert.assertNotNull(cmd.getSubcommands().get("message"));
        Assert.assertNotNull(cmd.getSubcommands().get("client"));
        Assert.assertNotNull(cmd.getSubcommands().get("acl"));
        Assert.assertNotNull(cmd.getSubcommands().get("broker"));
        Assert.assertNotNull(cmd.getSubcommands().get("metrics"));
        Assert.assertNotNull(cmd.getSubcommands().get("namespace"));
    }

    @Test
    public void testGetCluster() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertNull(command.getCluster());

        command.clusterOverride = "test-cluster";
        Assert.assertEquals("test-cluster", command.getCluster());
    }

    @Test
    public void testIsDryRun() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertFalse(command.isDryRun());

        command.dryRun = true;
        Assert.assertTrue(command.isDryRun());
    }

    @Test
    public void testIsYes() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertFalse(command.isYes());

        command.yes = true;
        Assert.assertTrue(command.isYes());
    }

    @Test
    public void testIsForce() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertFalse(command.isForce());

        command.force = true;
        Assert.assertTrue(command.isForce());
    }

    @Test
    public void testCallReturnsZero() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertEquals(0, command.call().intValue());
    }

    @Test
    public void testHelpOptionRegistered() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);
        // With mixinStandardHelpOptions, --help should work
        int exitCode = cmd.execute("--help");
        Assert.assertEquals(0, exitCode);
    }

    @Test
    public void testVersionOption() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);
        int exitCode = cmd.execute("--version");
        Assert.assertEquals(0, exitCode);
    }

    @Test
    public void testOutputFormatFromArgs() {
        RmqctlCommand command = new RmqctlCommand();
        new CommandLine(command).parseArgs("--output", "json");
        Assert.assertEquals(OutputFormatter.Format.JSON, command.getOutputFormat());
    }

    @Test
    public void testOutputFormatCaseInsensitive() {
        RmqctlCommand command = new RmqctlCommand();
        command.outputFormat = "JSON";
        Assert.assertEquals(OutputFormatter.Format.JSON, command.getOutputFormat());

        command.outputFormat = "YAML";
        Assert.assertEquals(OutputFormatter.Format.YAML, command.getOutputFormat());

        command.outputFormat = "Table";
        Assert.assertEquals(OutputFormatter.Format.TABLE, command.getOutputFormat());
    }

    @Test
    public void testExplainSubcommand() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);
        Assert.assertNotNull(cmd.getSubcommands().get("explain"));
    }

    @Test
    public void testGenerateCompletionSubcommand() {
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);
        Assert.assertNotNull(cmd.getSubcommands().get("generate-completion"));
    }

    @Test
    public void testMainWithNoArgs() {
        // main() with no args should print usage and exit with 0
        // We can't easily test System.exit, but we can test the command execution
        RmqctlCommand command = new RmqctlCommand();
        CommandLine cmd = new CommandLine(command);
        int exitCode = cmd.execute();
        Assert.assertEquals(0, exitCode);
    }

    @Test
    public void testOutputFormatEmptyString() {
        RmqctlCommand command = new RmqctlCommand();
        command.outputFormat = "";
        Assert.assertEquals(OutputFormatter.Format.TABLE, command.getOutputFormat());
    }

    @Test
    public void testOutputFormatWhitespace() {
        RmqctlCommand command = new RmqctlCommand();
        command.outputFormat = "  ";
        Assert.assertEquals(OutputFormatter.Format.TABLE, command.getOutputFormat());
    }

    @Test
    public void testClusterOverrideNull() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertNull(command.getCluster());
    }

    @Test
    public void testAllBooleanDefaults() {
        RmqctlCommand command = new RmqctlCommand();
        Assert.assertFalse(command.isDryRun());
        Assert.assertFalse(command.isYes());
        Assert.assertFalse(command.isForce());
    }

    @Test
    public void testCommandDescription() {
        CommandLine.Command cmd = RmqctlCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull(cmd);
        Assert.assertTrue(java.util.Arrays.toString(cmd.description()).contains("kubectl-style"));
    }

    @Test
    public void testVersionValue() {
        CommandLine.Command cmd = RmqctlCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertEquals("rmqctl 2.1.1", cmd.version()[0]);
    }

    @Test
    public void testMixinStandardHelpOptions() {
        CommandLine.Command cmd = RmqctlCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertTrue(cmd.mixinStandardHelpOptions());
    }
}
