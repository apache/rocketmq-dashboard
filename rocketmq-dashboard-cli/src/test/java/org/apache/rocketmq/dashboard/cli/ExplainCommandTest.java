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

public class ExplainCommandTest {

    private ByteArrayOutputStream capturedOut;
    private PrintStream originalOut;

    @Before
    public void setUp() {
        capturedOut = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(capturedOut));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testExplainTopic() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("RESOURCE: TOPIC"));
        Assert.assertTrue(output.contains("VERB:"));
    }

    @Test
    public void testExplainGroup() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "group";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("RESOURCE: GROUP"));
    }

    @Test
    public void testExplainCluster() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "cluster";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("RESOURCE: CLUSTER"));
    }

    @Test
    public void testExplainNamespace() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "namespace";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("RESOURCE: NAMESPACE"));
    }

    @Test
    public void testExplainBroker() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "broker";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testExplainMessage() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "message";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testExplainClient() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "client";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testExplainAcl() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "acl";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testExplainMetrics() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "metrics";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testExplainUnknownResource() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "unknown";
        int result = cmd.call();
        Assert.assertEquals(1, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("Unknown resource: unknown"));
        Assert.assertTrue(output.contains("Available resources:"));
    }

    @Test
    public void testExplainOutputContainsParameters() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("Parameters:"));
        Assert.assertTrue(output.contains("--cluster"));
    }

    @Test
    public void testExplainOutputContainsRiskLevel() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("Read-only") || output.contains("Controlled mutation") || output.contains("Dangerous operation"));
    }

    @Test
    public void testExplainOutputContainsSeparator() throws Exception {
        ExplainCommand cmd = new ExplainCommand();
        cmd.resource = "topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("============================================================"));
    }
}