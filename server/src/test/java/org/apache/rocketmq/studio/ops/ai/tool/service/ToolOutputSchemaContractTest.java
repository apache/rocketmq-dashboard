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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.metrics.MetricDataVO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclRuleItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.AlertRuleListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerDescribeOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ResetOffsetOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceCapabilitiesOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryDlqOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryDlqOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageSendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageTraceOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverConfigItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.AuditItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicQueueStatsItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicRouteItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.handler.dashboard.DashboardSummaryToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.handler.nameserver.NameserverListToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden output-contract sweep: one representative sample per catalog tool, validated through
 * the same {@link ToolSchemaValidator} pipeline the runtime filter chain uses. Keeps the yaml
 * output schemas and the Java contract records in a 40/40 bijection — a schema or record drift
 * fails here with the exact offending tool and field.
 */
class ToolOutputSchemaContractTest {

    private static final String INSTANCE = "instance-a";
    private static final long TIMESTAMP = 1784246400000L;

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new LegacyJackson2Config().jackson2ObjectMapper(),
            JsonMapper.builder().build());

    @Test
    void validatesEveryToolOutputSampleTest() {
        samples().forEach((tool, outputs) -> outputs.forEach(output -> {
            ToolDefinition definition = catalog.getDefinition(tool);
            validator.validateOutput(definition, output);
        }));
    }

    @Test
    void coversEveryCatalogToolTest() {
        Set<String> covered = new TreeSet<>(samples().keySet());
        Set<String> declared = new TreeSet<>(catalog.list().stream()
                .map(ToolDefinition::name)
                .toList());
        assertThat(covered).isEqualTo(declared);
    }

    private static Map<String, List<Object>> samples() {
        Map<String, List<Object>> samples = new LinkedHashMap<>();

        AclRuleItem aclRule = new AclRuleItem(
                "1", "alice", "orders", "TOPIC", "LITERAL",
                List.of("PUB"), "ALLOW", INSTANCE, "v2", "2026-08-22T08:00:00");
        AclRuleVO aclRuleVO = AclRuleVO.builder()
                .id(1L)
                .principal("alice")
                .resource("orders")
                .resourceType("TOPIC")
                .resourcePattern("LITERAL")
                .actions(List.of("PUB"))
                .decision("ALLOW")
                .scope(INSTANCE)
                .aclVersion("v2")
                .gmtCreate(LocalDateTime.of(2026, 8, 22, 8, 0))
                .build();
        samples.put("rmq.acl.get", List.of(aclRule));
        samples.put("rmq.acl.list", List.of(new PageOutput<>(1, 20, 1L, List.of(aclRule))));
        samples.put("rmq.acl.create", List.of(planned(), executed(aclRuleVO)));
        samples.put("rmq.acl.update", List.of(planned(), executed(aclRuleVO)));
        samples.put("rmq.acl.delete", List.of(planned(), executedVoid()));

        AclUserItem user = new AclUserItem("1", "alice", true, List.of(INSTANCE));
        samples.put("rmq.user.get", List.of(user));
        samples.put("rmq.user.list", List.of(new ListOutput<>(List.of(user))));
        samples.put("rmq.user.create", List.of(planned(), executed(user)));
        samples.put("rmq.user.delete", List.of(planned(), executedVoid()));

        samples.put("rmq.alert.rule.list", List.of(new ListOutput<>(List.of(
                new AlertRuleListItem(1L, "consumer-lag", "consumer.lag.total", ">",
                        1000.0, "count", "5m", List.of("dingtalk"), true, "lag alert")))));

        samples.put("rmq.audit.list", List.of(new PageOutput<>(1, 20, 1L, List.of(
                new AuditItem(1L, "2026-08-22T08:00:00", "admin", "CREATE_TOPIC", "TOPIC",
                        "orders", INSTANCE, "{}", "SUCCESS", null)))));

        samples.put("rmq.dashboard.summary", List.of(new DashboardSummaryToolHandler.Output(
                List.of(new DashboardSummaryToolHandler.Cluster(
                        INSTANCE, INSTANCE, "V4_DIRECT", "healthy",
                        1, 0, 2, 3, 10L, 9L, "V5_5_0", List.of(1, 2))),
                new DashboardSummaryToolHandler.Stats(
                        1, 1, 1, 0, 1, 2, 3, 100L, 5L, 10L, 9L))));

        samples.put("rmq.instance.capabilities", List.of(
                new InstanceCapabilitiesOutput(INSTANCE, List.of("TOPIC_MANAGEMENT"))));
        samples.put("rmq.instance.metrics", List.of(MetricDataVO.builder()
                .resultType("matrix")
                .series(List.of(MetricDataVO.MetricSeriesVO.builder()
                        .labels(Map.of("cluster", "rmq-a"))
                        .values(List.of(MetricDataVO.MetricSampleVO.builder()
                                .timestamp(1784246400.0)
                                .value("1.5")
                                .build()))
                        .build()))
                .warnings(List.of())
                .build()));

        BrokerVO broker = BrokerVO.builder()
                .name("broker-a")
                .addr("127.0.0.1:10911")
                .version("V5_5_0")
                .status(BrokerStatus.running)
                .diskUsage(0.25)
                .tpsIn(10)
                .tpsOut(9)
                .putMessagesToday(100)
                .putMessagesYesterday(90)
                .getMessagesToday(80)
                .getMessagesYesterday(70)
                .runtimeStatsAvailable(true)
                .build();
        samples.put("rmq.broker.list", List.of(new ListOutput<>(List.of(broker))));
        samples.put("rmq.broker.describe", List.of(new BrokerDescribeOutput(
                "broker-a", "127.0.0.1:10911", "V5_5_0", BrokerStatus.running,
                0.25, 10L, 9L, 100L, 90L, 80L, 70L, true)));
        samples.put("rmq.broker.config", List.of(new BrokerConfigOutput(
                "rmq-a", true, false, 1, 1,
                List.of("maxMessageSize"),
                List.of(new BrokerConfigOutput.BrokerStatus(
                        "broker-a", "127.0.0.1:10911", true, null)),
                List.of(new BrokerConfigOutput.ConfigDifference(
                        "maxMessageSize", "maxMessageSize",
                        List.of(new BrokerConfigOutput.ConfigValue(
                                "broker-a", "127.0.0.1:10911", true, "4194304")))))));

        samples.put("rmq.cluster.list", List.of(new ListOutput<>(List.of(
                new ClusterListItem("rmq-a", "127.0.0.1:10911", "broker-a", 0L, "V5_5_0")))));

        samples.put("rmq.nameserver.list", List.of(new ListOutput<>(List.of(
                new NameserverListToolHandler.Item(
                        "127.0.0.1:9876", "127.0.0.1:9876", "127.0.0.1:9876",
                        null, null, "UNKNOWN", null)))));
        samples.put("rmq.nameserver.config", List.of(new ListOutput<>(List.of(
                new NameserverConfigItem("127.0.0.1:9876", Map.of("orderMessageEnable", "false"))))));

        samples.put("rmq.proxy.list", List.of(new ListOutput<>(List.of(ProxyVO.builder()
                .addr("127.0.0.1:8081")
                .status(ClusterStatus.healthy)
                .connections(3)
                .grpcPort(8081)
                .remotingPort(8080)
                .build()))));
        samples.put("rmq.proxy.config", List.of(new ListOutput<>(List.of(
                new ProxyConfigItem("127.0.0.1:8081", "healthy", 3, 8081, 8080,
                        true, true, "V5_5_0")))));

        TopicListItem topicItem = new TopicListItem(
                "orders", INSTANCE, TopicType.NORMAL, 8, 8, TopicPerm.RW, 100L, 1.5, 2);
        samples.put("rmq.topic.list", List.of(new ListOutput<>(List.of(topicItem))));
        TopicRouteItem route = new TopicRouteItem(
                "broker-a", "127.0.0.1:10911", "127.0.0.1:10911",
                Map.of(0L, "127.0.0.1:10911"), List.of(0L), 1, 8, 8,
                "RW", 6, true, true, 0);
        samples.put("rmq.topic.route", List.of(new ListOutput<>(List.of(route))));
        samples.put("rmq.topic.detail", List.of(new TopicDetailOutput(
                INSTANCE, "orders", INSTANCE, TopicType.NORMAL, 8, 8, TopicPerm.RW,
                100L, 1.5, 2, "order topic",
                List.of(new TopicDetailOutput.ConsumerGroup(
                        "cg-orders", "CLUSTERING", "CLUSTERING", 1.5, 50L, true)),
                List.of(new TopicDetailOutput.Route(
                        "broker-a", "127.0.0.1:10911", "127.0.0.1:10911",
                        Map.of(0L, "127.0.0.1:10911"), List.of(0L), 1, 8, 8,
                        "RW", 6, true, true, 0)),
                List.of(new TopicQueueStatsItem("broker-a", 0, 0L, 100L, TIMESTAMP)))));
        samples.put("rmq.topic.update", List.of(planned(), executed(new TopicOutput(
                "orders", INSTANCE, "NORMAL", 8, 8, "RW", "order topic"))));
        samples.put("rmq.topic.delete", List.of(planned(), executedVoid()));

        GroupListItem groupItem = new GroupListItem(
                "cg-orders", INSTANCE, SubscriptionMode.Push, ConsumeType.CLUSTERING,
                16, 2, 100L, List.of("orders"));
        // onlineInstances carries a -1 sentinel when the connection inventory is unavailable.
        // ToolValidationFilter validates every tool result, so a schema that rejects -1 would
        // fail the whole call at runtime instead of reporting the unknown state.
        GroupListItem unknownConnectionsItem = new GroupListItem(
                "cg-orders", INSTANCE, SubscriptionMode.Push, ConsumeType.CLUSTERING,
                16, -1, 100L, List.of("orders"));
        samples.put("rmq.group.list", List.of(
                new ListOutput<>(List.of(groupItem)),
                new ListOutput<>(List.of(unknownConnectionsItem))));
        samples.put("rmq.group.detail", List.of(new GroupDetailOutput(
                INSTANCE, "cg-orders", SubscriptionMode.Push, ConsumeType.CLUSTERING,
                2, 100L, List.of("orders"), "TAG", "Concurrently", 16, 0,
                List.of(new GroupDetailOutput.Subscription(
                        "orders", "*", "TAG", "STANDARD", "CONSISTENT")),
                List.of(new GroupDetailOutput.Instance(
                        "client-1", "gRPC", "127.0.0.1:50000", List.of("orders"),
                        "2026-08-22T09:30:00", Map.of("orders", 10L))),
                new GroupDetailOutput.Health("HEALTHY", List.of()),
                List.of(groupItem),
                new GroupDetailOutput.Progress(100L, List.of(
                        new GroupDetailOutput.QueueProgress("broker-a", 0, 120L, 90L, 30L))),
                new GroupDetailOutput.Clients(1, List.of(
                        new GroupDetailOutput.Client(
                                "client-1", "gRPC", "127.0.0.1:50000", "JAVA", "5.0.7",
                                true, List.of("orders"), "2026-08-22T09:30:00",
                                Map.of("orders", 10L))))),
                new GroupDetailOutput(
                        INSTANCE, "cg-orders", SubscriptionMode.Push, ConsumeType.CLUSTERING,
                        -1, 100L, List.of("orders"), "TAG", "Concurrently", 16, 0,
                        List.of(new GroupDetailOutput.Subscription(
                                "orders", "*", "TAG", "STANDARD", "CONSISTENT")),
                        List.of(),
                        new GroupDetailOutput.Health(
                                "UNKNOWN", List.of("Consumer connection information is unavailable.")),
                        List.of(unknownConnectionsItem),
                        null,
                        null)));
        samples.put("rmq.group.update", List.of(planned(), executed(groupItem)));
        samples.put("rmq.group.delete", List.of(planned(), executedVoid()));
        samples.put("rmq.group.reset_offset", List.of(
                planned(),
                executed(new ResetOffsetOutput("cg-orders", "orders", TIMESTAMP, true))));

        MessageItem message = new MessageItem(
                "MSG-1", "orders", "tagA", "keyA", TIMESTAMP,
                "127.0.0.1:10911", "127.0.0.1:50000", "aGVsbG8=", "BASE64", false, 5);
        MessageQueryOutput.Item withoutBody = new MessageQueryOutput.Item(
                "MSG-1", "orders", "tagA", "keyA", TIMESTAMP,
                "127.0.0.1:10911", "127.0.0.1:50000", null, null, null, 5);
        MessageQueryOutput.Item withBody = new MessageQueryOutput.Item(
                "MSG-1", "orders", "tagA", "keyA", TIMESTAMP,
                "127.0.0.1:10911", "127.0.0.1:50000", "aGVsbG8=", "BASE64", false, 5);
        samples.put("rmq.message.query", List.of(
                new MessageQueryOutput(new PageOutput<>(1, 20, 1L, List.of(withoutBody)), false),
                new MessageQueryOutput(new PageOutput<>(1, 20, 1L, List.of(withBody)), false)));
        samples.put("rmq.message.query_by_topic", List.of(
                new MessageQueryOutput(new PageOutput<>(1, 20, 200L, List.of(withoutBody)), true),
                new MessageQueryOutput(new PageOutput<>(1, 20, 200L, List.of(withBody)), true)));
        samples.put("rmq.message.query_by_offset", List.of(new ListOutput<>(List.of(message))));
        samples.put("rmq.message.query_dlq", List.of(
                MessageQueryDlqOutput.ofGroups(INSTANCE, 1, 20, 1L, List.of(
                        new MessageQueryDlqOutput.DlqGroupItem(
                                "cg-orders", "%DLQ%cg-orders", 5, 16, "CONSUMED",
                                true, "2026-08-22T09:30:00"))),
                MessageQueryDlqOutput.ofMessages(INSTANCE, "cg-orders", 1, 20, 1L, List.of(
                        new MessageQueryDlqOutput.DlqMessageItem(
                                "MSG-9", "%DLQ%cg-orders", 0, 12L, TIMESTAMP, "keyA", "hello")))));
        samples.put("rmq.message.trace", List.of(new MessageTraceOutput(
                "MSG-1",
                List.of(new MessageTraceOutput.Node(
                        "SEND", TIMESTAMP, "SUCCESS", 5L, "message sent")),
                List.of(new MessageTraceOutput.ConsumerStatus(
                        "cg-orders", "CONSUMED", TIMESTAMP, 0)))));
        samples.put("rmq.message.send", List.of(planned(), executed(
                new MessageSendOutput("MSG-1", "OFF-1", TIMESTAMP))));
        samples.put("rmq.message.redelivery", List.of(planned(), executed(
                new MessageRedeliveryOutput("MSG-1", "MSG-2", "%RETRY%cg-orders"))));
        samples.put("rmq.message.redelivery_dlq", List.of(planned(), executed(
                new MessageRedeliveryDlqOutput(5, 5, 0, "ALL_RESENT", false, 0))));

        return samples;
    }

    private static ToolPlan plan() {
        return ToolPlan.builder("preview summary")
                .impact("one impact")
                .build();
    }

    private static <R> MutationOutput<R> planned() {
        return new MutationOutput<>(
                MutationOutput.Status.PLANNED, INSTANCE, plan(), "confirm-token-1", null);
    }

    private static <R> MutationOutput<R> executed(R result) {
        return new MutationOutput<>(
                MutationOutput.Status.EXECUTED, INSTANCE, plan(), null, result);
    }

    private static MutationOutput<Void> executedVoid() {
        return executed(null);
    }

}
