/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.rocketmq.common.CheckRocksdbCqWriteResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.TopicConfigSerializeWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerRocksdbCheckServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final ClusterInfo cluster = new ClusterInfo();
    private final TopicConfigSerializeWrapper topics = new TopicConfigSerializeWrapper();
    private final Properties config = new Properties();
    private BrokerRocksdbCheckService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new BrokerRocksdbCheckService(new RuntimeAdminClientResolver(repository, factory,
                new MqAdminProperties(), mock(MqClientPool.class)), new OperationAuditService(audits));
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        cluster.setBrokerAddrTable(new HashMap<>());
        cluster.getBrokerAddrTable().put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "master:10911", 1L, "replica:10911"))));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        topics.getTopicConfigTable().put("orders", new TopicConfig("orders"));
        topics.getTopicConfigTable().put("payments", new TopicConfig("payments"));
        when(admin.getAllTopicConfig("master:10911", 5000)).thenReturn(topics);
        config.setProperty("rocksdbCQDoubleWriteEnable", "true");
        config.setProperty("combineCQLoadingCQTypes", "default;defaultRocksDB");
        when(admin.getBrokerConfig("master:10911")).thenReturn(config);
        var result = new CheckRocksdbCqWriteResult();
        result.setCheckStatus(2);
        when(admin.checkRocksdbCqWriteProgress("master:10911", "orders", 1000L)).thenReturn(result);
    }

    private BrokerRocksdbCheckService.Preview preview() {
        return service.preview("instance-a", "broker-a", "master:10911");
    }

    private BrokerRocksdbCheckService.Request request() {
        return new BrokerRocksdbCheckService.Request("instance-a", "broker-a", "master:10911", "orders",
                "1000", preview().settings(), true);
    }

    @Test
    void readsOnlySelectedNodeConfigurationWithoutStartingScan() throws Exception {
        assertThat(preview().topics()).containsExactly("orders", "payments");
        assertThat(preview().eligible()).isTrue();
        verify(admin, never()).checkRocksdbCqWriteProgress(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void acceptedIsOnlyAnAsyncReceiptWithExactScopeAndAudit() throws Exception {
        var result = service.submit(request());
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.brokerStatus()).isEqualTo(2);
        assertThat(result.checkFromMillis()).isEqualTo("1000");
        assertThat(result.submittedAt()).isBeforeOrEqualTo(result.receivedAt());
        verify(admin).checkRocksdbCqWriteProgress("master:10911", "orders", 1000L);
        verify(audits).insert(any(RmqOperationAudit.class));
    }

    @Test
    void disabledOrIncompleteStoreConfigurationCannotStart() {
        var request = request();
        config.setProperty("rocksdbCQDoubleWriteEnable", "false");
        assertThat(preview().eligible()).isFalse();
        assertThatThrownBy(() -> service.submit(request)).hasMessageContaining("unsupported or changed");
        config.setProperty("rocksdbCQDoubleWriteEnable", "true");
        config.setProperty("combineCQLoadingCQTypes", "default");
        assertThat(preview().eligible()).isFalse();
        assertThatThrownBy(() -> service.submit(request)).hasMessageContaining("unsupported or changed");
    }

    @Test
    void settingsAndTopicAreRecheckedBeforeSubmission() throws Exception {
        var request = request();
        config.setProperty("combineCQLoadingCQTypes", "default;defaultRocksDB;futureStore");
        assertThatThrownBy(() -> service.submit(request)).hasMessageContaining("changed");
        config.setProperty("combineCQLoadingCQTypes", "default;defaultRocksDB");
        topics.getTopicConfigTable().remove("orders");
        assertThatThrownBy(() -> service.submit(request)).hasMessageContaining("no longer configured");
        verify(admin, never()).checkRocksdbCqWriteProgress(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void missingMetadataIsNotHealthyOrEligible() throws Exception {
        config.remove("rocksdbCQDoubleWriteEnable");
        assertThatThrownBy(this::preview).hasMessageContaining("configuration is unavailable");
        config.setProperty("rocksdbCQDoubleWriteEnable", "invalid");
        assertThatThrownBy(this::preview).hasMessageContaining("configuration is unavailable");
        config.setProperty("rocksdbCQDoubleWriteEnable", "true");
        when(admin.getAllTopicConfig("master:10911", 5000)).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("topics are unavailable");
        when(admin.getBrokerConfig("master:10911")).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("configuration is unavailable");
    }

    @Test
    void validatesExplicitConfirmationAndOneTopic() {
        var settings = preview().settings();
        assertThatThrownBy(() -> service.submit(new BrokerRocksdbCheckService.Request("instance-a", "broker-a",
                "master:10911", "", "1000", settings, true))).hasMessageContaining("Select one topic");
        assertThatThrownBy(() -> service.submit(new BrokerRocksdbCheckService.Request("instance-a", "broker-a",
                "master:10911", "orders", "1000", settings, false))).hasMessageContaining("confirm");
        assertThatThrownBy(() -> service.submit(new BrokerRocksdbCheckService.Request("instance-a", "broker-a",
                "master:10911", "orders", "1000", null, true))).hasMessageContaining("review");
    }

    @Test
    void rejectsInvalidFutureOrOverflowingCheckpointWithoutTruncation() {
        var settings = preview().settings();
        for (String time : new String[] {null, "", "1.5", "-1", "0", "9223372036854775808", "9223372036854775807"}) {
            assertThatThrownBy(() -> service.submit(new BrokerRocksdbCheckService.Request("instance-a", "broker-a",
                    "master:10911", "orders", time, settings, true))).hasMessageContaining("epoch milliseconds");
        }
    }

    @Test
    void removedRegistrationCannotRetargetAScan() throws Exception {
        var request = request();
        cluster.getBrokerAddrTable().clear();
        assertThatThrownBy(() -> service.submit(request)).hasMessageContaining("not registered");
        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("registry is unavailable");
        assertThatThrownBy(() -> service.preview("instance-a", "", "master:10911")).hasMessageContaining("required");
    }

    @Test
    void unexpectedNativeStatusIsPreservedWithoutClaimingConsistency() throws Exception {
        var response = new CheckRocksdbCqWriteResult();
        response.setCheckStatus(0);
        response.setCheckResult("No store to compare");
        when(admin.checkRocksdbCqWriteProgress("master:10911", "orders", 1000L)).thenReturn(response);
        var receipt = service.submit(request());
        assertThat(receipt.status()).isEqualTo("BROKER_RESPONSE");
        assertThat(receipt.brokerStatus()).isZero();
        assertThat(receipt.brokerRemark()).isEqualTo("No store to compare");
    }

    @Test
    void lostOrMissingResponseIsUnknownAndNeverRetried() throws Exception {
        when(admin.checkRocksdbCqWriteProgress("master:10911", "orders", 1000L)).thenReturn(null);
        assertThat(service.submit(request()).status()).isEqualTo("UNKNOWN");
        doThrow(new org.apache.rocketmq.remoting.exception.RemotingTimeoutException("master:10911", 5000))
                .when(admin).checkRocksdbCqWriteProgress("master:10911", "orders", 1000L);
        assertThat(service.submit(request()).status()).isEqualTo("UNKNOWN");
        verify(admin, org.mockito.Mockito.times(2)).checkRocksdbCqWriteProgress("master:10911", "orders", 1000L);
    }

    @Test
    void auditFailureDoesNotLoseAnAcceptedResponse() {
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        assertThat(service.submit(request()).status()).isEqualTo("ACCEPTED");
    }

    @Test
    void interruptedReadsAndSubmissionRestoreFlag() throws Exception {
        var request = request();
        doThrow(new InterruptedException()).when(admin).examineBrokerClusterInfo();
        try {
            assertThatThrownBy(this::preview).hasMessageContaining("preview was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            assertThatThrownBy(() -> service.submit(request)).hasMessageContaining("preflight was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            org.mockito.Mockito.doReturn(cluster).when(admin).examineBrokerClusterInfo();
            doThrow(new InterruptedException()).when(admin).checkRocksdbCqWriteProgress("master:10911", "orders", 1000L);
            assertThat(service.submit(request).status()).isEqualTo("UNKNOWN");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void httpContractSeparatesPreviewAndSubmission() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokerRocksdbCheckController(service)).build();
        mvc.perform(get("/api/brokers/rocksdb-check").param("instanceId", "instance-a")
                        .param("brokerName", "broker-a").param("address", "master:10911"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eligible").value(true));
        mvc.perform(post("/api/brokers/rocksdb-check").contentType("application/json")
                        .content(new ObjectMapper().writeValueAsString(request())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }
}
