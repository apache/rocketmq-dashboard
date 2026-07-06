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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;

public class CommandClassesTest extends AbstractCliTest {

    @Before
    public void setUp() throws Exception {
        resetConfig();
    }

    // ---- ClusterCommand ----

    @Test
    public void testClusterCommandAnnotation() {
        CommandLine.Command cmd = ClusterCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on ClusterCommand", cmd);
        Assert.assertEquals("cluster", cmd.name());
    }

    @Test
    public void testClusterList() throws Exception {
        ClusterCommand.ListCmd cmd = withParent(new ClusterCommand.ListCmd(), newParentWithRoot(ClusterCommand.class));
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testClusterDescribe() throws Exception {
        ClusterCommand.DescribeCmd cmd = new ClusterCommand.DescribeCmd();
        withParent(cmd, newParentWithRoot(ClusterCommand.class));
        cmd.clusterName = null;
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testClusterDescribeWithName() throws Exception {
        ClusterCommand.DescribeCmd cmd = new ClusterCommand.DescribeCmd();
        withParent(cmd, newParentWithRoot(ClusterCommand.class));
        cmd.clusterName = "non-existent-cluster";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    // ---- TopicCommand ----

    @Test
    public void testTopicCommandAnnotation() {
        CommandLine.Command cmd = TopicCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on TopicCommand", cmd);
        Assert.assertEquals("topic", cmd.name());
    }

    @Test
    public void testTopicList() throws Exception {
        TopicCommand.ListCmd cmd = withParent(new TopicCommand.ListCmd(), newParentWithRoot(TopicCommand.class));
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testTopicDescribe() throws Exception {
        TopicCommand.DescribeCmd cmd = new TopicCommand.DescribeCmd();
        cmd.topicName = "test-topic";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testTopicCreate() throws Exception {
        TopicCommand.CreateCmd cmd = new TopicCommand.CreateCmd();
        withParent(cmd, newParentWithRoot(TopicCommand.class));
        cmd.topicName = "new-topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testTopicUpdate() throws Exception {
        TopicCommand.UpdateCmd cmd = new TopicCommand.UpdateCmd();
        withParent(cmd, newParentWithRoot(TopicCommand.class));
        cmd.topicName = "existing-topic";
        cmd.readQueue = 16;
        cmd.writeQueue = 16;
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testTopicDelete() throws Exception {
        TopicCommand.DeleteCmd cmd = new TopicCommand.DeleteCmd();
        withParent(cmd, newParentWithRoot(TopicCommand.class));
        cmd.topicName = "doomed-topic";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    // ---- GroupCommand ----

    @Test
    public void testGroupCommandAnnotation() {
        CommandLine.Command cmd = GroupCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on GroupCommand", cmd);
        Assert.assertEquals("group", cmd.name());
    }

    @Test
    public void testGroupList() throws Exception {
        GroupCommand.ListCmd cmd = new GroupCommand.ListCmd();
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testGroupDescribe() throws Exception {
        GroupCommand.DescribeCmd cmd = new GroupCommand.DescribeCmd();
        cmd.groupName = "test-group";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testGroupCreate() throws Exception {
        GroupCommand.CreateCmd cmd = new GroupCommand.CreateCmd();
        withParent(cmd, newParentWithRoot(GroupCommand.class));
        cmd.groupName = "new-group";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testGroupUpdate() throws Exception {
        GroupCommand.UpdateCmd cmd = new GroupCommand.UpdateCmd();
        withParent(cmd, newParentWithRoot(GroupCommand.class));
        cmd.groupName = "existing-group";
        cmd.retryMax = 10;
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testGroupResetOffset() throws Exception {
        GroupCommand.ResetOffsetCmd cmd = new GroupCommand.ResetOffsetCmd();
        withParent(cmd, newParentWithRoot(GroupCommand.class));
        cmd.groupName = "test-group";
        cmd.topicName = "test-topic";
        cmd.timestamp = "2026-07-04 10:00:00";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testGroupResetOffsetByInterval() throws Exception {
        GroupCommand.ResetOffsetCmd cmd = new GroupCommand.ResetOffsetCmd();
        withParent(cmd, newParentWithRoot(GroupCommand.class));
        cmd.groupName = "test-group";
        cmd.topicName = "test-topic";
        cmd.intervalHours = 24L;
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testGroupResetOffsetDefaultToLatest() throws Exception {
        GroupCommand.ResetOffsetCmd cmd = new GroupCommand.ResetOffsetCmd();
        withParent(cmd, newParentWithRoot(GroupCommand.class));
        cmd.groupName = "test-group";
        cmd.topicName = "test-topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testGroupDelete() throws Exception {
        GroupCommand.DeleteCmd cmd = new GroupCommand.DeleteCmd();
        withParent(cmd, newParentWithRoot(GroupCommand.class));
        cmd.groupName = "doomed-group";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    // ---- MessageCommand ----

    @Test
    public void testMessageCommandAnnotation() {
        CommandLine.Command cmd = MessageCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on MessageCommand", cmd);
        Assert.assertEquals("message", cmd.name());
    }

    @Test
    public void testMessageQueryById() throws Exception {
        MessageCommand.QueryByIdCmd cmd = new MessageCommand.QueryByIdCmd();
        cmd.messageId = "test-msg-id";
        cmd.topicName = "test-topic";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testMessageQueryByTime() throws Exception {
        MessageCommand.QueryByTimeCmd cmd = new MessageCommand.QueryByTimeCmd();
        cmd.topicName = "test-topic";
        cmd.beginT = "2026-07-04 10:00:00";
        cmd.endT = "2026-07-04 11:00:00";
        cmd.maxNum = 10;
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testMessageResend() throws Exception {
        MessageCommand.ResendCmd cmd = new MessageCommand.ResendCmd();
        withParent(cmd, newParentWithRoot(MessageCommand.class));
        cmd.messageId = "test-msg-id";
        cmd.groupName = "test-group";
        cmd.topicName = "test-topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    // ---- ClientCommand ----

    @Test
    public void testClientCommandAnnotation() {
        CommandLine.Command cmd = ClientCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on ClientCommand", cmd);
        Assert.assertEquals("client", cmd.name());
    }

    @Test
    public void testClientList() throws Exception {
        ClientCommand.ListCmd cmd = new ClientCommand.ListCmd();
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testClientListWithGroupFilter() throws Exception {
        ClientCommand.ListCmd cmd = new ClientCommand.ListCmd();
        cmd.group = "test-group";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testClientDescribe() throws Exception {
        ClientCommand.DescribeCmd cmd = new ClientCommand.DescribeCmd();
        cmd.clientId = "test-client-id";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    // ---- AclCommand ----

    @Test
    public void testAclCommandAnnotation() {
        CommandLine.Command cmd = AclCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on AclCommand", cmd);
        Assert.assertEquals("acl", cmd.name());
    }

    @Test
    public void testAclList() throws Exception {
        AclCommand.ListCmd cmd = new AclCommand.ListCmd();
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testAclCreate() throws Exception {
        AclCommand.CreateCmd cmd = new AclCommand.CreateCmd();
        cmd.username = "test-user";
        cmd.topicPerm = "PUB|SUB";
        cmd.groupPerm = "SUB";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testAclUpdate() throws Exception {
        AclCommand.UpdateCmd cmd = new AclCommand.UpdateCmd();
        cmd.username = "test-user";
        cmd.topicPerm = "PUB";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testAclDelete() throws Exception {
        AclCommand.DeleteCmd cmd = new AclCommand.DeleteCmd();
        cmd.username = "doomed-user";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    // ---- BrokerCommand ----

    @Test
    public void testBrokerCommandAnnotation() {
        CommandLine.Command cmd = BrokerCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on BrokerCommand", cmd);
        Assert.assertEquals("broker", cmd.name());
    }

    @Test
    public void testBrokerList() throws Exception {
        BrokerCommand.ListCmd cmd = new BrokerCommand.ListCmd();
        withParent(cmd, newParentWithRoot(BrokerCommand.class));
        try {
            cmd.call();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No cluster specified"));
        }
    }

    @Test
    public void testBrokerDescribe() throws Exception {
        BrokerCommand.DescribeCmd cmd = new BrokerCommand.DescribeCmd();
        withParent(cmd, newParentWithRoot(BrokerCommand.class));
        cmd.brokerName = "broker-a";
        try {
            cmd.call();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No cluster specified"));
        }
    }

    @Test
    public void testBrokerConfig() throws Exception {
        BrokerCommand.ConfigCmd cmd = new BrokerCommand.ConfigCmd();
        withParent(cmd, newParentWithRoot(BrokerCommand.class));
        cmd.brokerName = "broker-a";
        try {
            cmd.call();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No cluster specified"));
        }
    }

    @Test
    public void testBrokerConfigWithFilter() throws Exception {
        BrokerCommand.ConfigCmd cmd = new BrokerCommand.ConfigCmd();
        withParent(cmd, newParentWithRoot(BrokerCommand.class));
        cmd.brokerName = "broker-a";
        cmd.filter = "flush";
        try {
            cmd.call();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No cluster specified"));
        }
    }

    // ---- MetricsCommand ----

    @Test
    public void testMetricsCommandAnnotation() {
        CommandLine.Command cmd = MetricsCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on MetricsCommand", cmd);
        Assert.assertEquals("metrics", cmd.name());
    }

    @Test
    public void testMetricsQueryCluster() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "cluster";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testMetricsQueryBroker() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "broker";
        cmd.resourceName = "broker-a";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testMetricsQueryTopic() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "topic";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testMetricsQueryConsumer() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "consumer";
        cmd.resourceName = "test-group";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testMetricsQueryClient() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "client";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testMetricsQuerySystem() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "system";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testMetricsQueryInvalidType() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = "invalid";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testMetricsQueryNullType() throws Exception {
        MetricsCommand.QueryCmd cmd = new MetricsCommand.QueryCmd();
        cmd.metricType = null;
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }

    // ---- NamespaceCommand ----

    @Test
    public void testNamespaceCommandAnnotation() {
        CommandLine.Command cmd = NamespaceCommand.class.getAnnotation(CommandLine.Command.class);
        Assert.assertNotNull("@Command annotation should be present on NamespaceCommand", cmd);
        Assert.assertEquals("namespace", cmd.name());
    }

    @Test
    public void testNamespaceList() throws Exception {
        NamespaceCommand.ListCmd cmd = new NamespaceCommand.ListCmd();
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testNamespaceCreate() throws Exception {
        NamespaceCommand.CreateCmd cmd = new NamespaceCommand.CreateCmd();
        cmd.namespace = "my-namespace";
        int result = cmd.call();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testNamespaceDelete() throws Exception {
        NamespaceCommand.DeleteCmd cmd = new NamespaceCommand.DeleteCmd();
        cmd.namespace = "doomed-ns";
        int result = cmd.call();
        Assert.assertEquals(1, result);
    }
}
