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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;

public class ConfigCommandTest extends AbstractCliTest {

    @Before
    public void setUp() throws Exception {
        resetConfig();
    }

    @Test
    public void testGetContextsEmpty() throws Exception {
        ConfigCommand.GetContexts cmd = new ConfigCommand.GetContexts();
        int exitCode = cmd.call();
        Assert.assertEquals(0, exitCode);
    }

    @Test
    public void testAddCluster() throws Exception {
        ConfigCommand.AddCluster cmd = new ConfigCommand.AddCluster();
        cmd.name = "test-cluster";
        cmd.namesrvAddr = "127.0.0.1:9876";
        cmd.proxyAddr = "127.0.0.1:8080";
        cmd.clusterType = "V4_NAMESRV";

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        int exitCode = cmd.call();

        System.setOut(oldOut);

        Assert.assertEquals(0, exitCode);
        String output = baos.toString();
        Assert.assertTrue(output.contains("test-cluster"));
        Assert.assertTrue(output.contains("added"));

        Path configFile = getTempHomeDir().resolve(".rmqctl").resolve("config.yaml");
        Assert.assertTrue("Config file should exist", Files.exists(configFile));
    }

    @Test
    public void testAddClusterDefaults() throws Exception {
        ConfigCommand.AddCluster cmd = new ConfigCommand.AddCluster();
        cmd.name = "default-cluster";
        cmd.namesrvAddr = "ns.example.com:9876";

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        int exitCode = cmd.call();

        System.setOut(oldOut);

        Assert.assertEquals(0, exitCode);
        String output = baos.toString();
        Assert.assertTrue(output.contains("default-cluster"));
    }

    @Test
    public void testSetContext() throws Exception {
        ConfigCommand.SetContext cmd = new ConfigCommand.SetContext();
        cmd.name = "dev";
        cmd.cluster = "dev-cluster";
        cmd.user = "dev-user";
        cmd.namespace = "dev-ns";

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        int exitCode = cmd.call();

        System.setOut(oldOut);

        Assert.assertEquals(0, exitCode);
        String output = baos.toString();
        Assert.assertTrue(output.contains("dev"));
        Assert.assertTrue(output.contains("set"));
    }

    @Test
    public void testUseContext() throws Exception {
        // Set up a context by writing config directly (avoids SnakeYAML global tag)
        writeConfig("clusters: {}\nusers: {}\n"
                + "contexts:\n"
                + "- name: prod\n"
                + "  cluster: prod-cluster\n"
                + "  user: admin\n");

        ConfigCommand.UseContext useCmd = new ConfigCommand.UseContext();
        useCmd.contextName = "prod";

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        int exitCode = useCmd.call();

        System.setOut(oldOut);

        Assert.assertEquals(0, exitCode);
        String output = baos.toString();
        Assert.assertTrue(output.contains("prod"));
        Assert.assertTrue(output.contains("Switched"));
    }

    @Test
    public void testGetContextsWithData() throws Exception {
        writeConfig("clusters:\n"
                + "  my-cluster:\n"
                + "    name: my-cluster\n"
                + "    namesrvAddr: 127.0.0.1:9876\n"
                + "    clusterType: V4_NAMESRV\n"
                + "users: {}\n"
                + "contexts:\n"
                + "- name: my-ctx\n"
                + "  cluster: my-cluster\n");

        ConfigCommand.GetContexts getCmd = new ConfigCommand.GetContexts();
        int exitCode = getCmd.call();
        Assert.assertEquals(0, exitCode);
    }

    @Test
    public void testSetContextWithoutUserAndNamespace() throws Exception {
        ConfigCommand.SetContext cmd = new ConfigCommand.SetContext();
        cmd.name = "minimal";
        cmd.cluster = "minimal-cluster";

        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        int exitCode = cmd.call();

        System.setOut(oldOut);

        Assert.assertEquals(0, exitCode);
        String output = baos.toString();
        Assert.assertTrue(output.contains("minimal"));
    }

    @Test
    public void testConfigSubcommandAnnotations() {
        CommandLine cmd = new CommandLine(new ConfigCommand());
        Assert.assertNotNull(cmd.getSubcommands().get("get-contexts"));
        Assert.assertNotNull(cmd.getSubcommands().get("use-context"));
        Assert.assertNotNull(cmd.getSubcommands().get("set-context"));
        Assert.assertNotNull(cmd.getSubcommands().get("add-cluster"));
    }
}
