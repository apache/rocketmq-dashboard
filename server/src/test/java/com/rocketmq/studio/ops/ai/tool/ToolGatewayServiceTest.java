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
package com.rocketmq.studio.ops.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.cluster.broker.ClusterService;
import com.rocketmq.studio.cluster.broker.ClusterVO;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import com.rocketmq.studio.common.domain.enums.ConsumeType;
import com.rocketmq.studio.common.domain.enums.SubscriptionMode;
import com.rocketmq.studio.common.domain.enums.TopicPerm;
import com.rocketmq.studio.common.domain.enums.TopicType;
import com.rocketmq.studio.common.exception.BusinessException;
import com.rocketmq.studio.instance.group.ConsumerGroupVO;
import com.rocketmq.studio.instance.topic.MetadataService;
import com.rocketmq.studio.instance.topic.TopicVO;
import com.rocketmq.studio.ops.ai.AiToolVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolGatewayServiceTest {

    private ToolCatalog catalog;
    private ClusterService clusterService;
    private MetadataService metadataService;
    private CapabilityResolver capabilityResolver;
    private ClusterListToolHandler clusterListHandler;
    private CapabilitiesToolHandler capabilitiesHandler;
    private TopicListToolHandler topicListHandler;
    private ConsumerGroupListToolHandler consumerGroupListHandler;
    private ToolGatewayService gateway;

    @BeforeEach
    void setUp() {
        catalog = canonicalCatalog();
        clusterService = mock(ClusterService.class);
        metadataService = mock(MetadataService.class);
        capabilityResolver = new CapabilityResolver(clusterService);
        clusterListHandler = new ClusterListToolHandler(clusterService);
        capabilitiesHandler = new CapabilitiesToolHandler(clusterService, capabilityResolver);
        topicListHandler = new TopicListToolHandler(metadataService);
        consumerGroupListHandler = new ConsumerGroupListToolHandler(metadataService);
        gateway = gateway(
                catalog,
                clusterListHandler,
                capabilitiesHandler,
                topicListHandler,
                consumerGroupListHandler);
    }

    @Test
    void discoveryWithoutClusterOnlyExposesClusterList() {
        assertThat(gateway.discover(null))
                .extracting(AiToolVO::getName)
                .containsExactly("rmq.cluster.list");
        verifyNoInteractions(clusterService);
    }

    @Test
    void discoveryWithClusterExposesRegisteredSupportedTools() {
        when(clusterService.getCluster("cluster-v5")).thenReturn(cluster(ClusterType.V5_PROXY_CLUSTER));

        assertThat(gateway.discover("cluster-v5"))
                .extracting(AiToolVO::getName)
                .containsExactly(
                        "rmq.cluster.list",
                        "rmq.capabilities",
                        "rmq.topic.list",
                        "rmq.group.list");
    }

    @Test
    void executesClusterListThroughADataMinimizingProjection() {
        ClusterVO cluster = cluster(ClusterType.V5_PROXY_CLUSTER);
        cluster.setEndpoint("do-not-expose.example:9876");
        when(clusterService.listClusters()).thenReturn(List.of(cluster));

        Object output = gateway.execute("rmq.cluster.list", Map.of());

        assertThat(output).isEqualTo(List.of(Map.of(
                "id", "cluster-v5",
                "name", "test",
                "type", "V5_PROXY_CLUSTER",
                "status", "healthy",
                "version", "5.2.0")));
        assertThat(output.toString()).doesNotContain("do-not-expose");
    }

    @Test
    void executesCapabilitiesWithAStableSortedCapabilityList() {
        when(clusterService.getCluster("cluster-v5")).thenReturn(cluster(ClusterType.V5_PROXY_CLUSTER));

        Object output = gateway.execute(
                "rmq.capabilities", Map.of("cluster", "cluster-v5"));

        assertThat(output).isEqualTo(Map.of(
                "cluster", "cluster-v5",
                "type", "V5_PROXY_CLUSTER",
                "version", "5.2.0",
                "capabilities", List.of(
                        "ACL_V2",
                        "CLUSTER_PROXY",
                        "GRPC",
                        "LITE_TOPIC",
                        "POP",
                        "REMOTING",
                        "ROCKETMQ_5")));
    }

    @Test
    void resolvesCapabilitiesForEveryExistingClusterType() {
        when(clusterService.getCluster("v4")).thenReturn(cluster("v4", ClusterType.V4_DIRECT));
        when(clusterService.getCluster("v5-local"))
                .thenReturn(cluster("v5-local", ClusterType.V5_PROXY_LOCAL));
        when(clusterService.getCluster("v5-cluster"))
                .thenReturn(cluster("v5-cluster", ClusterType.V5_PROXY_CLUSTER));

        assertThat(capabilityResolver.resolve("v4"))
                .containsExactly("REMOTING", "ROCKETMQ_4");
        assertThat(capabilityResolver.resolve("v5-local"))
                .containsExactly(
                        "ACL_V2",
                        "GRPC",
                        "LITE_TOPIC",
                        "LOCAL_PROXY",
                        "POP",
                        "REMOTING",
                        "ROCKETMQ_5");
        assertThat(capabilityResolver.resolve("v5-cluster"))
                .containsExactly(
                        "ACL_V2",
                        "CLUSTER_PROXY",
                        "GRPC",
                        "LITE_TOPIC",
                        "POP",
                        "REMOTING",
                        "ROCKETMQ_5");
    }

    @Test
    void rejectsClusterWithMissingType() {
        when(clusterService.getCluster("unknown")).thenReturn(cluster("unknown", null));

        assertThatThrownBy(() -> capabilityResolver.resolve("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster type is unavailable");
    }

    @Test
    void executesTopicListThroughADataMinimizingProjection() {
        when(clusterService.getCluster("cluster-v5")).thenReturn(cluster(ClusterType.V5_PROXY_CLUSTER));
        TopicVO topic = topic();
        topic.setRemark("do-not-expose");
        when(metadataService.listTopics("cluster-v5", "NORMAL", "order"))
                .thenReturn(List.of(topic));

        Object output = gateway.execute("rmq.topic.list", Map.of(
                "cluster", "cluster-v5",
                "type", "NORMAL",
                "search", "order"));

        assertThat(output).isEqualTo(List.of(Map.of(
                "name", "order-topic",
                "namespace", "default",
                "clusterId", "cluster-v5",
                "type", "NORMAL",
                "writeQueues", 8,
                "readQueues", 8,
                "perm", "RW",
                "messageCount", 1200L,
                "tps", 23.5D,
                "consumerGroupCount", 3)));
        assertThat(output.toString()).doesNotContain("do-not-expose");
    }

    @Test
    void rejectsTopicListWithoutAClusterBeforeHandlerRuns() {
        assertThatThrownBy(() -> gateway.execute("rmq.topic.list", Map.of("type", "NORMAL")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input validation failed");
        verifyNoInteractions(metadataService);
    }

    @Test
    void executesConsumerGroupListThroughADataMinimizingProjection() {
        when(clusterService.getCluster("cluster-v5")).thenReturn(cluster(ClusterType.V5_PROXY_CLUSTER));
        ConsumerGroupVO group = consumerGroup();
        group.setDelaySeconds(30);
        when(metadataService.listConsumerGroups("cluster-v5", "order")).thenReturn(List.of(group));

        Object output = gateway.execute("rmq.group.list", Map.of(
                "cluster", "cluster-v5",
                "search", "order"));

        assertThat(output).isEqualTo(List.of(Map.of(
                "name", "cg-order",
                "namespace", "default",
                "clusterId", "cluster-v5",
                "subscriptionMode", "Push",
                "consumeType", "CLUSTERING",
                "onlineInstances", 2,
                "totalLag", 42L,
                "subscribedTopics", List.of("order-topic"),
                "retryMaxTimes", 16)));
        assertThat(output.toString()).doesNotContain("delaySeconds");
    }

    @Test
    void rejectsConsumerGroupListWithoutAClusterBeforeHandlerRuns() {
        assertThatThrownBy(() -> gateway.execute("rmq.group.list", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input validation failed");
        verifyNoInteractions(metadataService);
    }

    @Test
    void rejectsCapabilitiesExecutionWhenClusterTypeIsMissing() {
        when(clusterService.getCluster("unknown")).thenReturn(cluster("unknown", null));

        assertThatThrownBy(() -> gateway.execute(
                "rmq.capabilities", Map.of("cluster", "unknown")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster type is unavailable");
    }

    @Test
    void rejectsClusterListEntriesWithMissingTypeUsingAStableError() {
        when(clusterService.listClusters()).thenReturn(List.of(cluster("unknown", null)));

        assertThatThrownBy(() -> gateway.execute("rmq.cluster.list", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cluster type is unavailable");
    }

    @Test
    void rejectsCapabilitiesWithoutRequiredClusterBeforeHandlerRuns() {
        assertThatThrownBy(() -> gateway.execute("rmq.capabilities", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input validation failed");
        verifyNoInteractions(clusterService);
    }

    @Test
    void rejectsUnexpectedInputPropertiesBeforeHandlerRuns() {
        assertThatThrownBy(() -> gateway.execute(
                "rmq.cluster.list", Map.of("endpoint", "attacker.example")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input validation failed");
        verifyNoInteractions(clusterService);
    }

    @Test
    void rejectsUnknownTools() {
        assertThatThrownBy(() -> gateway.execute("rmq.unknown", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tool not found");
    }

    @Test
    void refusesNonL1CatalogEntriesEvenWhenAHandlerIsRegistered() throws IOException {
        String yaml = canonicalCatalogText().replaceFirst("riskLevel: L1", "riskLevel: L2");
        ToolCatalog l2Catalog = ToolCatalog.load(
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)),
                new ClassPathResource("tool-catalog/rmq-tools.schema.json"));
        ToolGatewayService l2Gateway = gateway(
                l2Catalog,
                clusterListHandler,
                capabilitiesHandler,
                topicListHandler,
                consumerGroupListHandler);

        assertThatThrownBy(() -> l2Gateway.execute("rmq.cluster.list", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only L1 tools are enabled");
        verifyNoInteractions(clusterService);
    }

    @Test
    void failsStartupForDuplicateHandlerNames() {
        assertThatThrownBy(() -> gateway(
                catalog,
                clusterListHandler,
                clusterListHandler,
                capabilitiesHandler,
                topicListHandler,
                consumerGroupListHandler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate handler");
    }

    @Test
    void failsStartupWhenCatalogAndHandlersDoNotMatch() {
        assertThatThrownBy(() -> gateway(catalog, clusterListHandler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing handler");
    }

    @Test
    void failsStartupWhenToolSchemaContainsAnUnresolvedReference() throws IOException {
        String yaml = canonicalCatalogText().replace(
                """
                            inputSchema:
                              type: object
                              additionalProperties: false
                        """,
                """
                            inputSchema:
                              $ref: '#/missing'
                        """);
        ToolCatalog invalidCatalog = ToolCatalog.load(
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)),
                new ClassPathResource("tool-catalog/rmq-tools.schema.json"));

        assertThatThrownBy(() -> gateway(
                invalidCatalog,
                clusterListHandler,
                capabilitiesHandler,
                topicListHandler,
                consumerGroupListHandler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("input schema")
                .hasMessageContaining("rmq.cluster.list");
    }

    @Test
    void failsStartupWhenToolSchemaViolatesTheJsonSchemaMetaSchema() throws IOException {
        String yaml = canonicalCatalogText().replace(
                """
                            inputSchema:
                              type: object
                        """,
                """
                            inputSchema:
                              type: unsupported
                        """);
        ToolCatalog invalidCatalog = ToolCatalog.load(
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)),
                new ClassPathResource("tool-catalog/rmq-tools.schema.json"));

        assertThatThrownBy(() -> gateway(
                invalidCatalog,
                clusterListHandler,
                capabilitiesHandler,
                topicListHandler,
                consumerGroupListHandler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("input schema")
                .hasMessageContaining("rmq.cluster.list");
    }

    @Test
    void validatesHandlerOutputAgainstTheCatalog() {
        ToolHandler invalidClusterListHandler = new ToolHandler() {
            @Override
            public String name() {
                return "rmq.cluster.list";
            }

            @Override
            public Object execute(Map<String, Object> input) {
                return Map.of("bad", "shape");
            }
        };
        ToolGatewayService invalidGateway = gateway(
                catalog,
                invalidClusterListHandler,
                capabilitiesHandler,
                topicListHandler,
                consumerGroupListHandler);

        assertThatThrownBy(() -> invalidGateway.execute("rmq.cluster.list", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output validation failed");
    }

    private ToolGatewayService gateway(ToolCatalog toolCatalog, ToolHandler... handlers) {
        return new ToolGatewayService(
                toolCatalog,
                capabilityResolver,
                new ObjectMapper(),
                List.of(handlers));
    }

    private static ToolCatalog canonicalCatalog() {
        return ToolCatalog.load(
                new ClassPathResource("tool-catalog/rmq-tools.yaml"),
                new ClassPathResource("tool-catalog/rmq-tools.schema.json"));
    }

    private static String canonicalCatalogText() throws IOException {
        return new ClassPathResource("tool-catalog/rmq-tools.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static ClusterVO cluster(ClusterType type) {
        return cluster("cluster-v5", type);
    }

    private static ClusterVO cluster(String id, ClusterType type) {
        ClusterVO cluster = ClusterVO.builder()
                .name("test")
                .type(type)
                .status(ClusterStatus.healthy)
                .version("5.2.0")
                .build();
        cluster.setId(id);
        return cluster;
    }

    private static TopicVO topic() {
        TopicVO topic = new TopicVO();
        topic.setName("order-topic");
        topic.setNamespace("default");
        topic.setClusterId("cluster-v5");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(8);
        topic.setReadQueues(8);
        topic.setPerm(TopicPerm.RW);
        topic.setMessageCount(1200L);
        topic.setTps(23.5D);
        topic.setConsumerGroupCount(3);
        return topic;
    }

    private static ConsumerGroupVO consumerGroup() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-order");
        group.setNamespace("default");
        group.setClusterId("cluster-v5");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setOnlineInstances(2);
        group.setTotalLag(42L);
        group.setSubscribedTopics(List.of("order-topic"));
        group.setRetryMaxTimes(16);
        return group;
    }
}
