/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.statictopic.TopicConfigAndQueueMapping;
import org.apache.rocketmq.remoting.protocol.statictopic.TopicQueueMappingDetail;
import org.apache.rocketmq.remoting.protocol.statictopic.LogicQueueMappingItem;
import org.apache.rocketmq.studio.instance.topic.StaticTopicMappingController;
import org.apache.rocketmq.studio.instance.topic.StaticTopicMappingService;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaticTopicMappingServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final ClusterInfo cluster = new ClusterInfo();
    private final TopicRouteData route = new TopicRouteData();
    private final TopicQueueMappingDetail detail = new TopicQueueMappingDetail("orders", 2, "broker-a", 9007199254740993L);
    private StaticTopicMappingService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new StaticTopicMappingService(new RuntimeAdminClientResolver(repository, factory,
                new MqAdminProperties(), mock(MqClientPool.class)));
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        cluster.setBrokerAddrTable(new HashMap<>());
        cluster.getBrokerAddrTable().put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "master:10911", 1L, "replica:10911"))));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        route.setBrokerDatas(new java.util.ArrayList<>(List.of(cluster.getBrokerAddrTable().get("broker-a"),
                new BrokerData("cluster-a", "broker-b", new HashMap<>(Map.of(0L, "route-untrusted:10911"))))));
        cluster.getBrokerAddrTable().put("broker-b", new BrokerData("cluster-a", "broker-b",
                new HashMap<>(Map.of(0L, "registered-b:10911"))));
        detail.getHostedQueues().put(0, List.of(
                new LogicQueueMappingItem(0, 1, "old-broker", 0, 0, 9007199254740993L, -1, -1),
                new LogicQueueMappingItem(1, 3, "broker-a", 9007199254740993L, 0, -1, -1, -1)));
        route.setTopicQueueMappingByBroker(new HashMap<>(Map.of("broker-a",
                TopicQueueMappingDetail.cloneAsMappingInfo(detail))));
        when(admin.examineTopicRouteInfo("orders")).thenReturn(route);
        when(admin.examineTopicConfig("master:10911", "orders"))
                .thenReturn(new TopicConfigAndQueueMapping(new TopicConfig("orders"), detail));
        when(admin.examineTopicConfig("registered-b:10911", "orders"))
                .thenReturn(new TopicConfigAndQueueMapping(new TopicConfig("orders"), null));
    }

    private StaticTopicMappingService.Snapshot inspect() {
        return service.inspect("instance-a", "orders");
    }

    @Test
    void preservesExactOffsetsAndHistoryOrderUsingRegisteredAddresses() throws Exception {
        var snapshot = inspect();
        assertThat(snapshot.partial()).isFalse();
        assertThat(snapshot.startedAt()).isBeforeOrEqualTo(snapshot.finishedAt());
        assertThat(snapshot.nodes()).extracting(StaticTopicMappingService.Node::brokerName).containsExactly("broker-a", "broker-b");
        var node = snapshot.nodes().get(0);
        assertThat(node.local().epoch()).isEqualTo("9007199254740993");
        assertThat(node.local().queues().get(0).lastMappedBroker()).isEqualTo("broker-a");
        assertThat(node.local().queues().get(0).segments()).extracting(StaticTopicMappingService.Segment::brokerName)
                .containsExactly("old-broker", "broker-a");
        assertThat(node.local().queues().get(0).segments().get(0).physicalEndExclusive()).isEqualTo("9007199254740993");
        assertThat(node.local().queues().get(0).segments().get(1).physicalEndExclusive()).isEqualTo("-1");
        assertThat(node.advertised().currentQueues()).containsExactly(new StaticTopicMappingService.CurrentQueue(0, 3));
        assertThat(snapshot.nodes().get(1).status()).isEqualTo("NO_MAPPING");
        verify(admin).examineTopicConfig("registered-b:10911", "orders");
        verify(admin, never()).examineTopicConfig("route-untrusted:10911", "orders");
        verify(admin, never()).examineTopicConfig("old-broker", "orders");
    }

    @Test
    void partialFailureRetainsOtherBrokerSamplesAndAdvertisements() throws Exception {
        doThrow(new org.apache.rocketmq.remoting.exception.RemotingTimeoutException("master:10911", 5000))
                .when(admin).examineTopicConfig("master:10911", "orders");
        var result = inspect();
        assertThat(result.partial()).isTrue();
        assertThat(result.nodes().get(0).status()).isEqualTo("UNAVAILABLE");
        assertThat(result.nodes().get(0).advertised()).isNotNull();
        assertThat(result.nodes().get(0).error()).isEqualTo("RemotingTimeoutException");
        assertThat(result.nodes().get(1).status()).isEqualTo("NO_MAPPING");
    }

    @Test
    void mappingAdvertisementAddsMissingRouteBrokerWithoutGuessingItsAddress() {
        var missing = new org.apache.rocketmq.remoting.protocol.statictopic.TopicQueueMappingInfo("orders", 2, "broker-c", 1);
        route.getTopicQueueMappingByBroker().put("broker-c", missing);
        var result = inspect();
        assertThat(result.partial()).isTrue();
        assertThat(result.nodes().get(2).brokerName()).isEqualTo("broker-c");
        assertThat(result.nodes().get(2).address()).isNull();
        assertThat(result.nodes().get(2).error()).contains("No registered master");
    }

    @Test
    void neverFallsBackToReplicaWhenMasterIsMissing() throws Exception {
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        assertThat(inspect().nodes().get(0).status()).isEqualTo("UNAVAILABLE");
        verify(admin, never()).examineTopicConfig("replica:10911", "orders");
    }

    @Test
    void plainOrMissingConfigIsNotEvidenceOfAnOrdinaryTopic() throws Exception {
        when(admin.examineTopicConfig("master:10911", "orders")).thenReturn(new TopicConfig("orders"));
        assertThat(inspect().nodes().get(0).status()).isEqualTo("UNAVAILABLE");
        when(admin.examineTopicConfig("master:10911", "orders")).thenReturn(null);
        assertThat(inspect().nodes().get(0).status()).isEqualTo("UNAVAILABLE");
        when(admin.examineTopicConfig("master:10911", "orders"))
                .thenReturn(new TopicConfigAndQueueMapping(new TopicConfig("other"), null));
        assertThat(inspect().nodes().get(0).status()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void routeAndLocalEpochsRemainIndependentIncludingDirtyFlag() {
        detail.setEpoch(9007199254740994L);
        detail.setDirty(true);
        detail.setScope("scope-new");
        var node = inspect().nodes().get(0);
        assertThat(node.advertised().epoch()).isEqualTo("9007199254740993");
        assertThat(node.local().epoch()).isEqualTo("9007199254740994");
        assertThat(node.local().dirty()).isTrue();
        assertThat(node.local().scope()).isEqualTo("scope-new");
    }

    @Test
    void undecidedOffsetsAndEmptyHostedMapArePreserved() {
        detail.getHostedQueues().put(1, List.of(new LogicQueueMappingItem(2, 4, "broker-a", -1, 0, -1, -1, -1)));
        assertThat(inspect().nodes().get(0).local().queues().get(1).segments().get(0).logicalStart()).isEqualTo("-1");
        detail.getHostedQueues().clear();
        assertThat(inspect().nodes().get(0).local().queues()).isEmpty();
    }

    @Test
    void invalidMappingIdentityAndHistoryAreUnavailableWithoutDiscardingOtherNodes() {
        detail.setTopic("other");
        assertThat(inspect().nodes().get(0).error()).contains("identity does not match");
        detail.setTopic("orders");
        detail.getHostedQueues().put(0, List.of());
        assertThat(inspect().nodes().get(0).error()).contains("invalid logical queue history");
        detail.getHostedQueues().put(0, java.util.Arrays.asList((LogicQueueMappingItem) null));
        assertThat(inspect().nodes().get(0).error()).contains("invalid mapping segment");
        detail.getHostedQueues().put(0, List.of(new LogicQueueMappingItem(0, -1, "broker-a", 0, 0, -1, -1, -1)));
        assertThat(inspect().nodes().get(0).error()).contains("invalid mapping segment");
        detail.setHostedQueues(null);
        assertThat(inspect().nodes().get(0).error()).contains("history is unavailable");
    }

    @Test
    void missingAdvertisementIsDistinctFromUnreadableAdvertisement() {
        route.setTopicQueueMappingByBroker(null);
        assertThat(inspect().nodes().get(0).advertised()).isNull();
        var advertised = TopicQueueMappingDetail.cloneAsMappingInfo(detail);
        advertised.setCurrIdMap(null);
        route.setTopicQueueMappingByBroker(Map.of("broker-a", advertised));
        assertThat(inspect().nodes().get(0).error()).contains("logical queue map is unavailable");
    }

    @Test
    void malformedOrMissingRouteCannotProduceAnEmptyHealthySnapshot() throws Exception {
        route.getBrokerDatas().clear();
        route.setTopicQueueMappingByBroker(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("No route brokers");
        route.getBrokerDatas().add(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("invalid broker identity");
        route.getBrokerDatas().clear();
        route.setTopicQueueMappingByBroker(new HashMap<>());
        route.getTopicQueueMappingByBroker().put("", null);
        assertThatThrownBy(this::inspect).hasMessageContaining("invalid mapping identity");
        when(admin.examineTopicRouteInfo("orders")).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("route or broker registry is unavailable");
        assertThatThrownBy(() -> service.inspect("instance-a", " ")).hasMessageContaining("Topic is required");
    }

    @Test
    void interruptionStopsFurtherSamplingAndRestoresThreadFlag() throws Exception {
        doThrow(new InterruptedException()).when(admin).examineTopicConfig("master:10911", "orders");
        try {
            assertThatThrownBy(this::inspect).hasMessageContaining("inspection was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).examineTopicConfig("registered-b:10911", "orders");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void readOnlyHttpContractSerializesLongValuesAsStrings() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new StaticTopicMappingController(service)).build();
        mvc.perform(get("/api/static-topic-mappings").param("instanceId", "instance-a").param("topic", "orders"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes[0].local.epoch").value("9007199254740993"))
                .andExpect(jsonPath("$.data.nodes[0].local.queues[0].segments[1].physicalEndExclusive").value("-1"));
        verify(admin, never()).createStaticTopic(anyString(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
