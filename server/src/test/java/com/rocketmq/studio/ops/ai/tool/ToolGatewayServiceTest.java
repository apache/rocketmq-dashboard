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
import com.rocketmq.studio.common.exception.BusinessException;
import com.rocketmq.studio.ops.ai.AiToolVO;
import com.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import com.rocketmq.studio.ops.dashboard.DashboardDataVO;
import com.rocketmq.studio.ops.dashboard.DashboardService;
import com.rocketmq.studio.ops.dashboard.DashboardStatsVO;
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
    private DashboardService dashboardService;
    private CapabilityResolver capabilityResolver;
    private ClusterListToolHandler clusterListHandler;
    private CapabilitiesToolHandler capabilitiesHandler;
    private DashboardSummaryToolHandler dashboardSummaryHandler;
    private ToolGatewayService gateway;

    @BeforeEach
    void setUp() {
        catalog = canonicalCatalog();
        clusterService = mock(ClusterService.class);
        dashboardService = mock(DashboardService.class);
        capabilityResolver = new CapabilityResolver(clusterService);
        clusterListHandler = new ClusterListToolHandler(clusterService);
        capabilitiesHandler = new CapabilitiesToolHandler(clusterService, capabilityResolver);
        dashboardSummaryHandler = new DashboardSummaryToolHandler(dashboardService);
        gateway = gateway(
                catalog, clusterListHandler, capabilitiesHandler, dashboardSummaryHandler);
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
                        "rmq.dashboard.summary");
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
    @SuppressWarnings("unchecked")
    void executesDashboardSummaryWithADataMinimizingProjection() {
        when(dashboardService.getDashboard()).thenReturn(DashboardDataVO.builder()
                .stats(DashboardStatsVO.builder()
                        .totalClusters(1)
                        .healthyClusters(1)
                        .totalBrokers(2)
                        .totalProxies(1)
                        .totalNameServers(1)
                        .totalTopics(3)
                        .totalConsumerGroups(4)
                        .totalMessagesToday(500L)
                        .messagesPerSecond(6L)
                        .tpsIn(7L)
                        .tpsOut(8L)
                        .build())
                .clusters(List.of(ClusterOverviewVO.builder()
                        .id("cluster-v5")
                        .name("test")
                        .type(ClusterType.V5_PROXY_CLUSTER)
                        .status(ClusterStatus.healthy)
                        .brokers(2)
                        .proxies(1)
                        .topics(3)
                        .groups(4)
                        .tpsIn(7)
                        .tpsOut(8)
                        .version("5.2.0")
                        .throughput(List.of(1, 2, 3))
                        .build()))
                .build());

        Object output = gateway.execute(
                "rmq.dashboard.summary", Map.of("cluster", "cluster-v5"));

        Map<String, Object> result = (Map<String, Object>) output;
        assertThat(result).containsOnlyKeys("cluster", "stats");
        Map<String, Object> cluster = (Map<String, Object>) result.get("cluster");
        assertThat(cluster).containsEntry("id", "cluster-v5");
        assertThat(cluster).containsEntry("name", "test");
        assertThat(cluster).containsEntry("type", "V5_PROXY_CLUSTER");
        assertThat(cluster).containsEntry("status", "healthy");
        assertThat(cluster).containsEntry("brokers", 2);
        assertThat(cluster).containsEntry("proxies", 1);
        assertThat(cluster).containsEntry("topics", 3);
        assertThat(cluster).containsEntry("groups", 4);
        assertThat(cluster).containsEntry("tpsIn", 7);
        assertThat(cluster).containsEntry("tpsOut", 8);
        assertThat(cluster).containsEntry("version", "5.2.0");
        assertThat(cluster).containsEntry("throughput", List.of(1, 2, 3));
        assertThat(cluster).doesNotContainKeys("endpoint");
        assertThat((Map<String, Object>) result.get("stats")).containsAllEntriesOf(Map.of(
                "totalMessagesToday", 500L,
                "messagesPerSecond", 6L,
                "tpsIn", 7L,
                "tpsOut", 8L));
    }

    @Test
    void rejectsDashboardSummaryWithoutRequiredClusterBeforeHandlerRuns() {
        assertThatThrownBy(() -> gateway.execute("rmq.dashboard.summary", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input validation failed");
        verifyNoInteractions(dashboardService);
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
                l2Catalog, clusterListHandler, capabilitiesHandler, dashboardSummaryHandler);

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
                dashboardSummaryHandler))
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
                dashboardSummaryHandler))
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
                dashboardSummaryHandler))
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
                dashboardSummaryHandler);

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
}
