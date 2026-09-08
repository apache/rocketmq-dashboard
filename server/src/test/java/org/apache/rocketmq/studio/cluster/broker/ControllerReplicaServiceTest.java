/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.BrokerReplicasInfo;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.header.controller.GetMetaDataResponseHeader;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerReplicaServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final Properties config = new Properties();
    private final ClusterInfo cluster = new ClusterInfo();
    private final BrokerReplicasInfo replicas = new BrokerReplicasInfo();
    private final GetMetaDataResponseHeader leader = new GetMetaDataResponseHeader(
            "controller-group", "n0", "controller-a:9878", true, "n0-controller-a:9878;n1-controller-b:9878");
    private final GetMetaDataResponseHeader follower = new GetMetaDataResponseHeader(
            "controller-group", "n0", "controller-a:9878", false, "n0-controller-a:9878;n1-controller-b:9878");
    private ControllerReplicaService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new ControllerReplicaService(new RuntimeAdminClientResolver(repository, factory,
                new MqAdminProperties(), mock(MqClientPool.class)));
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        cluster.setBrokerAddrTable(new HashMap<>());
        cluster.getBrokerAddrTable().put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "master:10911", 1L, "replica:10911"))));
        config.setProperty("enableControllerMode", "true");
        config.setProperty("controllerAddr", "controller-b:9878;controller-a:9878;controller-a:9878");
        config.setProperty("secretKey", "must-not-be-returned");
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        when(admin.getBrokerConfig("master:10911")).thenReturn(config);
        when(admin.getControllerMetaData("controller-a:9878")).thenReturn(leader);
        when(admin.getControllerMetaData("controller-b:9878")).thenReturn(follower);
        replicas.addReplicaInfo("broker-a", new BrokerReplicasInfo.ReplicasInfo(9007199254740993L,
                "master:10911", 12, 18,
                new ArrayList<>(List.of(new BrokerReplicasInfo.ReplicaIdentity("broker-a", 9007199254740993L,
                        "master:10911", true))),
                new ArrayList<>(List.of(new BrokerReplicasInfo.ReplicaIdentity("broker-a", 2L,
                        "replica:10911", false)))));
        when(admin.getInSyncStateData("controller-a:9878", List.of("broker-a"))).thenReturn(replicas);
    }

    private ControllerReplicaSnapshot inspect() { return service.inspect("instance-a", "broker-a"); }

    @Test
    void readsConfiguredEndpointsAndKeepsIdentityPrecisionAndLivenessSeparate() throws Exception {
        var result = inspect();
        assertThat(result.mode()).isEqualTo("ENABLED");
        assertThat(result.configSource()).isEqualTo("master:10911");
        assertThat(result.agreement()).isEqualTo("MATCHING");
        assertThat(result.controllers()).extracting(ControllerReplicaSnapshot.Node::address)
                .containsExactly("controller-a:9878", "controller-b:9878");
        assertThat(result.membership().masterBrokerId()).isEqualTo("9007199254740993");
        assertThat(result.membership().masterEpoch()).isEqualTo(12);
        assertThat(result.membership().syncStateSetEpoch()).isEqualTo(18);
        assertThat(result.membership().replicas().getFirst().brokerId()).isEqualTo("2");
        assertThat(result.membership().replicas().getFirst().inSyncSet()).isFalse();
        assertThat(result.membership().replicas().getFirst().alive()).isFalse();
        assertThat(result.membershipError()).isNull();
        verify(admin, times(1)).getControllerMetaData("controller-a:9878");
        verify(admin).getInSyncStateData("controller-a:9878", List.of("broker-a"));
    }

    @Test
    void permitsInspectionThroughRegisteredReplicaWhenMasterIsAbsent() throws Exception {
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        when(admin.getBrokerConfig("replica:10911")).thenReturn(config);
        assertThat(inspect().configSource()).isEqualTo("replica:10911");
    }

    @Test
    void disabledUnknownOrUnconfiguredControllerModeDoesNotProbeControllers() throws Exception {
        config.setProperty("enableControllerMode", "false");
        assertThat(inspect().mode()).isEqualTo("DISABLED");
        config.remove("enableControllerMode");
        assertThat(inspect().mode()).isEqualTo("UNKNOWN");
        config.setProperty("enableControllerMode", "true");
        config.setProperty("controllerAddr", "");
        assertThat(inspect().controllers()).isEmpty();
        verify(admin, never()).getControllerMetaData(anyString());
    }

    @Test
    void reportsPartialMetadataWithoutLosingReachableMembership() throws Exception {
        when(admin.getControllerMetaData("controller-b:9878"))
                .thenThrow(new MQBrokerException(ResponseCode.NO_PERMISSION, "secret remark"));
        var result = inspect();
        assertThat(result.agreement()).isEqualTo("PARTIAL");
        assertThat(result.controllers().getLast().error()).contains("Broker response code:").doesNotContain("secret");
        assertThat(result.membership()).isNotNull();
    }

    @Test
    void discoversMembershipThroughAnotherConfiguredNodeWhenTheFirstTimesOut() throws Exception {
        when(admin.getControllerMetaData("controller-a:9878")).thenThrow(
                new org.apache.rocketmq.remoting.exception.RemotingTimeoutException("controller-a:9878", 3000));
        when(admin.getInSyncStateData("controller-b:9878", List.of("broker-a"))).thenReturn(replicas);
        var result = inspect();
        assertThat(result.agreement()).isEqualTo("PARTIAL");
        assertThat(result.controllers().getFirst().error()).contains("RemotingTimeoutException");
        assertThat(result.membership().discoveryAddress()).isEqualTo("controller-b:9878");
    }

    @Test
    void invalidReplicaIdentityCannotBeReportedAsACompleteMembership() {
        var replica = replicas.getReplicasInfoTable().get("broker-a").getInSyncReplicas().getFirst();
        replica.setBrokerName("different-broker");
        assertThat(inspect().membershipError()).contains("identity is invalid");
        replica.setBrokerName("broker-a");
        replica.setBrokerId(null);
        assertThat(inspect().membershipError()).contains("identity is invalid");
    }

    @Test
    void divergentLeaderViewsAreNotLabelledMatching() {
        follower.setControllerLeaderId("n1");
        follower.setControllerLeaderAddress("controller-b:9878");
        assertThat(inspect().agreement()).isEqualTo("DIVERGENT");
    }

    @Test
    void preservesMetadataWhenMembershipIsUnavailable() throws Exception {
        when(admin.getInSyncStateData("controller-a:9878", List.of("broker-a")))
                .thenThrow(new MQBrokerException(ResponseCode.SYSTEM_ERROR, "leader changed"));
        var result = inspect();
        assertThat(result.controllers()).hasSize(2);
        assertThat(result.membership()).isNull();
        assertThat(result.membershipError()).contains("Broker response code:");
    }

    @Test
    void emptyControllerRepliesAreNotSuccessfulObservations() throws Exception {
        when(admin.getControllerMetaData(anyString())).thenReturn(null);
        var result = inspect();
        assertThat(result.agreement()).isEqualTo("UNAVAILABLE");
        assertThat(result.controllers()).allMatch(node -> node.error() != null);
        verify(admin, never()).getInSyncStateData(anyString(), anyList());
    }

    @Test
    void noReportedLeaderSkipsMembershipQuery() throws Exception {
        leader.setControllerLeaderAddress(null);
        follower.setControllerLeaderAddress("");
        assertThat(inspect().membershipError()).contains("No controller reported");
        verify(admin, never()).getInSyncStateData(anyString(), anyList());
    }

    @Test
    void unavailableReplicaRecordAndContradictorySetsAreExplicitErrors() {
        var info = replicas.getReplicasInfoTable().remove("broker-a");
        assertThat(inspect().membershipError()).contains("no complete replica record");
        replicas.addReplicaInfo("broker-a", info);
        info.getNotInSyncReplicas().add(info.getInSyncReplicas().getFirst());
        assertThat(inspect().membershipError()).contains("both sets");
        assertThat(inspect().membership()).isNull();
    }

    @Test
    void nullableLivenessIsPreservedAndDoesNotRemoveSyncMembership() {
        var replica = replicas.getReplicasInfoTable().get("broker-a").getInSyncReplicas().getFirst();
        replica.setAlive(null);
        var result = inspect().membership().replicas().getLast();
        assertThat(result.inSyncSet()).isTrue();
        assertThat(result.alive()).isNull();
    }

    @Test
    void rejectsUnregisteredBrokerAndMissingConfiguration() throws Exception {
        assertThatThrownBy(() -> service.inspect("instance-a", "other"))
                .hasMessageContaining("not registered");
        assertThatThrownBy(() -> service.inspect("instance-a", ""))
                .hasMessageContaining("required");
        when(admin.getBrokerConfig("master:10911")).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("configuration is unavailable");
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().clear();
        assertThatThrownBy(this::inspect).hasMessageContaining("no registered address");
        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("metadata is unavailable");
    }

    @Test
    void interruptsRatherThanContinuingWithAFalsePartialResult() throws Exception {
        doThrow(new InterruptedException()).when(admin).getControllerMetaData("controller-a:9878");
        try {
            assertThatThrownBy(this::inspect).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).getControllerMetaData("controller-b:9878");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedMembershipAlsoRestoresInterruptFlag() throws Exception {
        doThrow(new InterruptedException()).when(admin).getInSyncStateData(anyString(), anyList());
        try {
            assertThatThrownBy(this::inspect).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void readOnlyControllerEndpointReturnsExactIdsWithoutOtherBrokerConfiguration() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ControllerReplicaController(service)).build();
        var result = mvc.perform(get("/api/brokers/controller-replicas").param("instanceId", "instance-a")
                        .param("brokerName", "broker-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membership.masterBrokerId").value("9007199254740993"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("must-not-be-returned", "secretKey");
    }
}
