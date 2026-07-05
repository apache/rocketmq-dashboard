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

import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class CliConfigTest {

    @Test
    public void testClassStructure() {
        CliConfig config = new CliConfig();
        Assert.assertNotNull(config);
        Assert.assertNotNull(config.getClusters());
        Assert.assertNotNull(config.getUsers());
        Assert.assertNotNull(config.getContexts());
        Assert.assertNull(config.getCurrentContext());
    }

    @Test
    public void testClusterEntry() {
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        entry.setName("test-cluster");
        entry.setNamesrvAddr("127.0.0.1:9876");
        entry.setProxyAddr("127.0.0.1:8080");
        entry.setClusterType("V4_NAMESRV");

        Assert.assertEquals("test-cluster", entry.getName());
        Assert.assertEquals("127.0.0.1:9876", entry.getNamesrvAddr());
        Assert.assertEquals("127.0.0.1:8080", entry.getProxyAddr());
        Assert.assertEquals("V4_NAMESRV", entry.getClusterType());
    }

    @Test
    public void testClusterEntryAllFields() {
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        entry.setName("prod");
        entry.setNamesrvAddr("ns.example.com:9876");
        entry.setProxyAddr("proxy.example.com:8080");
        entry.setClusterType("V5_PROXY");

        Assert.assertEquals("prod", entry.getName());
        Assert.assertEquals("ns.example.com:9876", entry.getNamesrvAddr());
        Assert.assertEquals("proxy.example.com:8080", entry.getProxyAddr());
        Assert.assertEquals("V5_PROXY", entry.getClusterType());
    }

    @Test
    public void testUserEntry() {
        CliConfig.UserEntry entry = new CliConfig.UserEntry();
        entry.setName("admin");
        entry.setAccessKey("AK123");
        entry.setSecretKey("SK456");

        Assert.assertEquals("admin", entry.getName());
        Assert.assertEquals("AK123", entry.getAccessKey());
        Assert.assertEquals("SK456", entry.getSecretKey());
    }

    @Test
    public void testContextEntry() {
        CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
        entry.setName("dev");
        entry.setCluster("dev-cluster");
        entry.setUser("dev-user");
        entry.setNamespace("dev-ns");

        Assert.assertEquals("dev", entry.getName());
        Assert.assertEquals("dev-cluster", entry.getCluster());
        Assert.assertEquals("dev-user", entry.getUser());
        Assert.assertEquals("dev-ns", entry.getNamespace());
    }

    @Test
    public void testContextEntryPartial() {
        CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
        entry.setName("minimal");
        entry.setCluster("test-cluster");

        Assert.assertEquals("minimal", entry.getName());
        Assert.assertEquals("test-cluster", entry.getCluster());
        Assert.assertNull(entry.getUser());
        Assert.assertNull(entry.getNamespace());
    }

    @Test
    public void testConfigSetters() {
        CliConfig config = new CliConfig();
        config.setCurrentContext("my-ctx");

        Assert.assertEquals("my-ctx", config.getCurrentContext());
    }

    @Test
    public void testClustersMap() {
        CliConfig config = new CliConfig();
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        entry.setName("c1");
        entry.setNamesrvAddr("addr1");

        config.getClusters().put("c1", entry);
        Map<String, CliConfig.ClusterEntry> clusters = config.getClusters();
        Assert.assertEquals(1, clusters.size());
        Assert.assertNotNull(clusters.get("c1"));
        Assert.assertEquals("addr1", clusters.get("c1").getNamesrvAddr());
    }

    @Test
    public void testContextsList() {
        CliConfig config = new CliConfig();
        CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
        entry.setName("ctx1");
        config.getContexts().add(entry);

        Assert.assertEquals(1, config.getContexts().size());
        Assert.assertEquals("ctx1", config.getContexts().get(0).getName());
    }

    @Test
    public void testUsersMap() {
        CliConfig config = new CliConfig();
        CliConfig.UserEntry entry = new CliConfig.UserEntry();
        entry.setName("user1");
        config.getUsers().put("user1", entry);

        Assert.assertEquals(1, config.getUsers().size());
        Assert.assertNotNull(config.getUsers().get("user1"));
    }

    @Test
    public void testToString() {
        CliConfig config = new CliConfig();
        config.setCurrentContext("test");
        String str = config.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("test"));
    }

    @Test
    public void testEqualsAndHashCode() {
        CliConfig c1 = new CliConfig();
        c1.setCurrentContext("ctx");
        CliConfig c2 = new CliConfig();
        c2.setCurrentContext("ctx");
        Assert.assertEquals(c1, c2);
        Assert.assertEquals(c1.hashCode(), c2.hashCode());
    }
}
