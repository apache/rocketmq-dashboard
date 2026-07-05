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
package org.apache.rocketmq.dashboard.cli.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.rocketmq.dashboard.cli.AbstractCliTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CliContextTest extends AbstractCliTest {

    @Before
    public void setUp() throws Exception {
        resetConfig();
    }

    @Test
    public void testInitWithNoConfigFile() {
        CliContext ctx = new CliContext();
        Assert.assertNotNull(ctx);
        Assert.assertNotNull(ctx.getConfig());
        Assert.assertNotNull(ctx.getContexts());
        Assert.assertTrue(ctx.getContexts().isEmpty());
        Assert.assertNull(ctx.getCurrentContext());
    }

    @Test
    public void testSaveAndLoad() throws Exception {
        CliContext ctx1 = new CliContext();
        ctx1.addCluster("test-cluster", "127.0.0.1:9876", "127.0.0.1:8080", "V4_NAMESRV");
        ctx1.setCurrentContext("test-cluster");

        Path configFile = getTempHomeDir().resolve(".rmqctl").resolve("config.yaml");
        Assert.assertTrue("Config file should exist", Files.exists(configFile));

        String savedContent = new String(Files.readAllBytes(configFile));
        Assert.assertTrue(savedContent.contains("test-cluster"));
        Assert.assertTrue(savedContent.contains("127.0.0.1:9876"));

        // Verify that the config can be reloaded by creating a new CliContext
        // with the saved file (need to strip the SnakeYAML global tag)
        String cleanContent = savedContent.replace("!!org.apache.rocketmq.dashboard.cli.context.CliConfig\n", "");
        Files.write(configFile, cleanContent.getBytes());

        CliContext ctx2 = new CliContext();
        Assert.assertNotNull(ctx2.getConfig());
        Assert.assertTrue(ctx2.getConfig().getClusters().containsKey("test-cluster"));
        CliConfig.ClusterEntry loaded = ctx2.getConfig().getClusters().get("test-cluster");
        Assert.assertNotNull(loaded);
        Assert.assertEquals("test-cluster", loaded.getName());
    }

    @Test
    public void testAddContext() {
        CliContext ctx = new CliContext();
        ctx.addContext("dev", "dev-cluster", "dev-user", "dev-ns");

        List<CliConfig.ContextEntry> contexts = ctx.getContexts();
        Assert.assertEquals(1, contexts.size());
        Assert.assertEquals("dev", contexts.get(0).getName());
        Assert.assertEquals("dev-cluster", contexts.get(0).getCluster());
        Assert.assertEquals("dev-user", contexts.get(0).getUser());
        Assert.assertEquals("dev-ns", contexts.get(0).getNamespace());
    }

    @Test
    public void testAddContextDuplicateName() {
        CliContext ctx = new CliContext();
        ctx.addContext("dev", "dev-cluster", "dev-user", "ns1");
        Assert.assertEquals(1, ctx.getContexts().size());

        ctx.addContext("dev", "updated-cluster", "updated-user", "ns2");
        Assert.assertEquals(1, ctx.getContexts().size());
        Assert.assertEquals("updated-cluster", ctx.getContexts().get(0).getCluster());
        Assert.assertEquals("updated-user", ctx.getContexts().get(0).getUser());
        Assert.assertEquals("ns2", ctx.getContexts().get(0).getNamespace());
    }

    @Test
    public void testSetCurrentContext() {
        CliContext ctx = new CliContext();
        ctx.setCurrentContext("my-context");
        Assert.assertEquals("my-context", ctx.getCurrentContext());
    }

    @Test
    public void testResolveCurrentContext() {
        CliContext ctx = new CliContext();
        ctx.addContext("prod", "prod-cluster", "prod-user", null);
        ctx.setCurrentContext("prod");

        CliConfig.ContextEntry resolved = ctx.resolveCurrentContext();
        Assert.assertNotNull(resolved);
        Assert.assertEquals("prod", resolved.getName());
        Assert.assertEquals("prod-cluster", resolved.getCluster());
        Assert.assertEquals("prod-user", resolved.getUser());
    }

    @Test
    public void testResolveCurrentContextNoCurrent() {
        CliContext ctx = new CliContext();
        CliConfig.ContextEntry resolved = ctx.resolveCurrentContext();
        Assert.assertNull(resolved);
    }

    @Test
    public void testResolveCurrentContextNotFound() {
        CliContext ctx = new CliContext();
        ctx.setCurrentContext("non-existent");

        CliConfig.ContextEntry resolved = ctx.resolveCurrentContext();
        Assert.assertNull(resolved);
    }

    @Test
    public void testAddCluster() {
        CliContext ctx = new CliContext();
        ctx.addCluster("prod", "ns1:9876", "proxy1:8080", "V4_NAMESRV");

        CliConfig.ClusterEntry cluster = ctx.getConfig().getClusters().get("prod");
        Assert.assertNotNull(cluster);
        Assert.assertEquals("prod", cluster.getName());
        Assert.assertEquals("ns1:9876", cluster.getNamesrvAddr());
        Assert.assertEquals("proxy1:8080", cluster.getProxyAddr());
        Assert.assertEquals("V4_NAMESRV", cluster.getClusterType());
    }

    @Test
    public void testGetClusterNames() {
        CliContext ctx = new CliContext();
        ctx.addCluster("cluster1", "ns1:9876", "", "V4");
        ctx.addCluster("cluster2", "ns2:9876", "", "V5");

        Set<String> names = ctx.getClusterNames();
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains("cluster1"));
        Assert.assertTrue(names.contains("cluster2"));
    }

    @Test
    public void testGetClusterNamesEmpty() {
        CliContext ctx = new CliContext();
        Set<String> names = ctx.getClusterNames();
        Assert.assertNotNull(names);
        Assert.assertTrue(names.isEmpty());
    }

    @Test
    public void testSaveConfigCreatesFile() {
        CliContext ctx = new CliContext();
        ctx.addCluster("test", "addr", "proxy", "type");

        Path configFile = getTempHomeDir().resolve(".rmqctl").resolve("config.yaml");
        Assert.assertTrue("Config file should exist at " + configFile, Files.exists(configFile));
    }

    @Test
    public void testLoadExistingConfig() throws Exception {
        String yamlContent =
                "currentContext: prod\n"
                + "clusters:\n"
                + "  prod:\n"
                + "    name: prod\n"
                + "    namesrvAddr: ns.prod.com:9876\n"
                + "    proxyAddr: proxy.prod.com:8080\n"
                + "    clusterType: V5_PROXY\n"
                + "contexts:\n"
                + "- name: prod\n"
                + "  cluster: prod\n"
                + "  user: admin\n"
                + "  namespace: default\n";

        writeConfig(yamlContent);

        CliContext ctx = new CliContext();
        Assert.assertEquals("prod", ctx.getCurrentContext());

        CliConfig.ClusterEntry cluster = ctx.getConfig().getClusters().get("prod");
        Assert.assertNotNull(cluster);
        Assert.assertEquals("ns.prod.com:9876", cluster.getNamesrvAddr());

        Assert.assertEquals(1, ctx.getContexts().size());
        Assert.assertEquals("prod", ctx.getContexts().get(0).getName());
    }

    @Test
    public void testResolveCluster() {
        CliContext ctx = new CliContext();
        ctx.addCluster("my-cluster", "ns:9876", "", "V4");
        ctx.addContext("main", "my-cluster", "admin", null);
        ctx.setCurrentContext("main");

        CliConfig.ClusterEntry cluster = ctx.resolveCluster();
        Assert.assertNotNull(cluster);
        Assert.assertEquals("my-cluster", cluster.getName());
        Assert.assertEquals("ns:9876", cluster.getNamesrvAddr());
    }

    @Test
    public void testResolveClusterNoContext() {
        CliContext ctx = new CliContext();
        CliConfig.ClusterEntry cluster = ctx.resolveCluster();
        Assert.assertNull(cluster);
    }

    @Test
    public void testGetConfig() {
        CliContext ctx = new CliContext();
        CliConfig config = ctx.getConfig();
        Assert.assertNotNull(config);
    }
}
