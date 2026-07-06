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

    // ---- ClusterEntry additional tests ----

    @Test
    public void testClusterEntryDefaults() {
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        Assert.assertNull(entry.getName());
        Assert.assertNull(entry.getNamesrvAddr());
        Assert.assertNull(entry.getProxyAddr());
        Assert.assertNull(entry.getClusterType());
    }

    @Test
    public void testClusterEntryEqualsAndHashCode() {
        CliConfig.ClusterEntry e1 = new CliConfig.ClusterEntry();
        e1.setName("c1");
        e1.setNamesrvAddr("addr1");
        e1.setProxyAddr("proxy1");
        e1.setClusterType("V4");

        CliConfig.ClusterEntry e2 = new CliConfig.ClusterEntry();
        e2.setName("c1");
        e2.setNamesrvAddr("addr1");
        e2.setProxyAddr("proxy1");
        e2.setClusterType("V4");

        Assert.assertEquals(e1, e2);
        Assert.assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void testClusterEntryToString() {
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        entry.setName("my-cluster");
        String str = entry.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("my-cluster"));
    }

    @Test
    public void testClusterEntryNotEqual() {
        CliConfig.ClusterEntry e1 = new CliConfig.ClusterEntry();
        e1.setName("c1");
        CliConfig.ClusterEntry e2 = new CliConfig.ClusterEntry();
        e2.setName("c2");
        Assert.assertNotEquals(e1, e2);
    }

    // ---- UserEntry additional tests ----

    @Test
    public void testUserEntryDefaults() {
        CliConfig.UserEntry entry = new CliConfig.UserEntry();
        Assert.assertNull(entry.getName());
        Assert.assertNull(entry.getAccessKey());
        Assert.assertNull(entry.getSecretKey());
    }

    @Test
    public void testUserEntryEqualsAndHashCode() {
        CliConfig.UserEntry e1 = new CliConfig.UserEntry();
        e1.setName("admin");
        e1.setAccessKey("AK1");
        e1.setSecretKey("SK1");

        CliConfig.UserEntry e2 = new CliConfig.UserEntry();
        e2.setName("admin");
        e2.setAccessKey("AK1");
        e2.setSecretKey("SK1");

        Assert.assertEquals(e1, e2);
        Assert.assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void testUserEntryToString() {
        CliConfig.UserEntry entry = new CliConfig.UserEntry();
        entry.setName("admin");
        String str = entry.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("admin"));
    }

    @Test
    public void testUserEntryNotEqual() {
        CliConfig.UserEntry e1 = new CliConfig.UserEntry();
        e1.setName("admin");
        CliConfig.UserEntry e2 = new CliConfig.UserEntry();
        e2.setName("user");
        Assert.assertNotEquals(e1, e2);
    }

    // ---- ContextEntry additional tests ----

    @Test
    public void testContextEntryDefaults() {
        CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
        Assert.assertNull(entry.getName());
        Assert.assertNull(entry.getCluster());
        Assert.assertNull(entry.getUser());
        Assert.assertNull(entry.getNamespace());
    }

    @Test
    public void testContextEntryEqualsAndHashCode() {
        CliConfig.ContextEntry e1 = new CliConfig.ContextEntry();
        e1.setName("dev");
        e1.setCluster("dev-cluster");
        e1.setUser("dev-user");
        e1.setNamespace("dev-ns");

        CliConfig.ContextEntry e2 = new CliConfig.ContextEntry();
        e2.setName("dev");
        e2.setCluster("dev-cluster");
        e2.setUser("dev-user");
        e2.setNamespace("dev-ns");

        Assert.assertEquals(e1, e2);
        Assert.assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void testContextEntryToString() {
        CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
        entry.setName("prod");
        entry.setCluster("prod-cluster");
        String str = entry.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("prod"));
    }

    @Test
    public void testContextEntryNotEqual() {
        CliConfig.ContextEntry e1 = new CliConfig.ContextEntry();
        e1.setName("ctx1");
        CliConfig.ContextEntry e2 = new CliConfig.ContextEntry();
        e2.setName("ctx2");
        Assert.assertNotEquals(e1, e2);
    }

    // ---- CliConfig additional tests ----

    @Test
    public void testCliConfigNotEqual() {
        CliConfig c1 = new CliConfig();
        c1.setCurrentContext("a");
        CliConfig c2 = new CliConfig();
        c2.setCurrentContext("b");
        Assert.assertNotEquals(c1, c2);
    }

    @Test
    public void testCliConfigNullCurrentContext() {
        CliConfig config = new CliConfig();
        Assert.assertNull(config.getCurrentContext());
    }

    @Test
    public void testClusterEntryWithNullProxyAddr() {
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        entry.setName("test");
        entry.setNamesrvAddr("addr");
        entry.setProxyAddr(null);
        entry.setClusterType("V4");
        Assert.assertNull(entry.getProxyAddr());
    }

    @Test
    public void testContextEntryWithNullNamespace() {
        CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
        entry.setName("test");
        entry.setCluster("cluster");
        entry.setUser("user");
        entry.setNamespace(null);
        Assert.assertNull(entry.getNamespace());
    }
}
