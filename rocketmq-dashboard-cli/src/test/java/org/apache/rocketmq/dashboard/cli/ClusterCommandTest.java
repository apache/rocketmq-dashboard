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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClusterCommandTest extends AbstractCliTest {

    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @Before
    public void setUp() throws Exception {
        resetConfig();
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void testListClustersEmpty() throws Exception {
        ClusterCommand.ListClusters cmd = new ClusterCommand.ListClusters();
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("No clusters configured"));
    }

    @Test
    public void testListClustersWithConfiguredClusters() throws Exception {
        writeConfig("clusters:\n"
                + "  prod:\n"
                + "    name: prod\n"
                + "    namesrvAddr: ns.prod.com:9876\n"
                + "    proxyAddr: proxy.prod.com:8080\n"
                + "    clusterType: V5_PROXY\n"
                + "  dev:\n"
                + "    name: dev\n"
                + "    namesrvAddr: 127.0.0.1:9876\n"
                + "    proxyAddr: ''\n"
                + "    clusterType: V4_NAMESRV\n"
                + "users: {}\ncontexts: []\n");

        ClusterCommand.ListClusters cmd = new ClusterCommand.ListClusters();
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("prod"));
        Assert.assertTrue(output.contains("dev"));
        Assert.assertTrue(output.contains("V5_PROXY"));
        Assert.assertTrue(output.contains("V4_NAMESRV"));
        Assert.assertTrue(output.contains("ns.prod.com:9876"));
        Assert.assertTrue(output.contains("127.0.0.1:9876"));
    }

    @Test
    public void testDescribeClusterNoNameNoContext() throws Exception {
        ClusterCommand.DescribeCluster cmd = new ClusterCommand.DescribeCluster();
        cmd.clusterName = null;
        int result = cmd.call();
        Assert.assertEquals(1, result);
        String err = capturedErr.toString();
        Assert.assertTrue(err.contains("No cluster specified") || err.contains("No current context"));
    }

    @Test
    public void testDescribeClusterNonExistent() throws Exception {
        ClusterCommand.DescribeCluster cmd = new ClusterCommand.DescribeCluster();
        cmd.clusterName = "non-existent";
        int result = cmd.call();
        Assert.assertEquals(1, result);
        String err = capturedErr.toString();
        Assert.assertTrue(err.contains("not found"));
    }

    @Test
    public void testDescribeClusterWithValidName() throws Exception {
        writeConfig("clusters:\n"
                + "  my-cluster:\n"
                + "    name: my-cluster\n"
                + "    namesrvAddr: 127.0.0.1:9876\n"
                + "    proxyAddr: 127.0.0.1:8080\n"
                + "    clusterType: V4_NAMESRV\n"
                + "users: {}\ncontexts: []\n");

        ClusterCommand.DescribeCluster cmd = new ClusterCommand.DescribeCluster();
        cmd.clusterName = "my-cluster";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("my-cluster"));
        Assert.assertTrue(output.contains("V4_NAMESRV"));
        Assert.assertTrue(output.contains("127.0.0.1:9876"));
        Assert.assertTrue(output.contains("127.0.0.1:8080"));
    }

    @Test
    public void testDescribeClusterWithNullProxyAddr() throws Exception {
        writeConfig("clusters:\n"
                + "  simple:\n"
                + "    name: simple\n"
                + "    namesrvAddr: 10.0.0.1:9876\n"
                + "    proxyAddr: ''\n"
                + "    clusterType: V4_NAMESRV\n"
                + "users: {}\ncontexts: []\n");

        ClusterCommand.DescribeCluster cmd = new ClusterCommand.DescribeCluster();
        cmd.clusterName = "simple";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("N/A"));
    }

    @Test
    public void testDescribeClusterFromCurrentContext() throws Exception {
        writeConfig("currentContext: dev\n"
                + "clusters:\n"
                + "  dev:\n"
                + "    name: dev\n"
                + "    namesrvAddr: 192.168.1.1:9876\n"
                + "    proxyAddr: ''\n"
                + "    clusterType: V4_NAMESRV\n"
                + "users: {}\n"
                + "contexts:\n"
                + "- name: dev\n"
                + "  cluster: dev\n"
                + "  user: admin\n"
                + "  namespace: default\n");

        ClusterCommand.DescribeCluster cmd = new ClusterCommand.DescribeCluster();
        cmd.clusterName = null;
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("dev"));
        Assert.assertTrue(output.contains("192.168.1.1:9876"));
    }

    @Test
    public void testDescribeClusterOutputFormat() throws Exception {
        writeConfig("clusters:\n"
                + "  prod:\n"
                + "    name: prod\n"
                + "    namesrvAddr: ns.example.com:9876\n"
                + "    proxyAddr: proxy.example.com:8081\n"
                + "    clusterType: V5_PROXY\n"
                + "users: {}\ncontexts: []\n");

        ClusterCommand.DescribeCluster cmd = new ClusterCommand.DescribeCluster();
        cmd.clusterName = "prod";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("cluster name"));
        Assert.assertTrue(output.contains("type"));
        Assert.assertTrue(output.contains("namesrv addr"));
        Assert.assertTrue(output.contains("proxy addr"));
    }
}