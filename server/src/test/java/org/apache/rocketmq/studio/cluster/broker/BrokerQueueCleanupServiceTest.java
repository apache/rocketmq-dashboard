/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import static org.apache.rocketmq.studio.cluster.broker.BrokerQueueCleanupService.Operation;
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

class BrokerQueueCleanupServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final ClusterInfo cluster = new ClusterInfo();
    private final TopicConfigSerializeWrapper topics = new TopicConfigSerializeWrapper();
    private BrokerQueueCleanupService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new BrokerQueueCleanupService(new RuntimeAdminClientResolver(repository, factory,
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
        when(admin.cleanUnusedTopicByAddr("master:10911")).thenReturn(true);
        when(admin.cleanExpiredConsumerQueueByAddr("master:10911")).thenReturn(true);
    }

    private BrokerQueueCleanupService.Preview preview() {
        return service.preview("instance-a", "broker-a", "master:10911");
    }
    private BrokerQueueCleanupService.Request request(Operation operation) {
        return new BrokerQueueCleanupService.Request("instance-a", "broker-a", "master:10911", operation,
                preview().configuredTopics(), "master:10911");
    }

    @Test
    void previewListsConfiguredTopicsWithoutCallingEitherCleanup() throws Exception {
        assertThat(preview().configuredTopics()).containsExactly("orders", "payments");
        verify(admin, never()).cleanUnusedTopicByAddr(anyString());
        verify(admin, never()).cleanExpiredConsumerQueueByAddr(anyString());
    }

    @Test
    void unusedCleanupTargetsOnlyTheReviewedNodeAndRecordsBrokerCompletion() throws Exception {
        var result = service.apply(request(Operation.UNUSED_TOPIC_QUEUES));
        assertThat(result.status()).isEqualTo("BROKER_REPORTED_COMPLETION");
        assertThat(result.address()).isEqualTo("master:10911");
        assertThat(result.operation()).isEqualTo(Operation.UNUSED_TOPIC_QUEUES);
        verify(admin).cleanUnusedTopicByAddr("master:10911");
        verify(admin, never()).cleanExpiredConsumerQueueByAddr(anyString());
        verify(audits).insert(any(RmqOperationAudit.class));
    }

    @Test
    void expiredCleanupUsesTheSeparateNativeCommand() throws Exception {
        assertThat(service.apply(request(Operation.EXPIRED_CONSUME_QUEUES)).status()).isEqualTo("BROKER_REPORTED_COMPLETION");
        verify(admin).cleanExpiredConsumerQueueByAddr("master:10911");
        verify(admin, never()).cleanUnusedTopicByAddr(anyString());
    }

    @Test
    void changedConfiguredTopicsRequireAWholeNewReview() throws Exception {
        var request = request(Operation.UNUSED_TOPIC_QUEUES);
        topics.getTopicConfigTable().remove("payments");
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("Configured topics changed");
        verify(admin, never()).cleanUnusedTopicByAddr(anyString());
    }

    @Test
    void wrongConfirmationOrMissingOperationCannotExecuteCleanup() throws Exception {
        assertThatThrownBy(() -> service.apply(new BrokerQueueCleanupService.Request("instance-a", "broker-a",
                "master:10911", Operation.UNUSED_TOPIC_QUEUES, List.of("orders"), "other:10911")))
                .hasMessageContaining("confirm its address");
        assertThatThrownBy(() -> service.apply(new BrokerQueueCleanupService.Request("instance-a", "broker-a",
                "master:10911", null, List.of("orders"), "master:10911")))
                .hasMessageContaining("select an operation");
        assertThatThrownBy(() -> service.apply(new BrokerQueueCleanupService.Request("instance-a", "broker-a",
                "master:10911", Operation.UNUSED_TOPIC_QUEUES, null, "master:10911")))
                .hasMessageContaining("Review the node");
        verify(admin, never()).cleanUnusedTopicByAddr(anyString());
    }

    @Test
    void removedOrMismatchedBrokerAddressCannotBeRetargeted() throws Exception {
        var request = request(Operation.EXPIRED_CONSUME_QUEUES);
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("not registered");
        assertThatThrownBy(() -> service.preview("instance-a", "other-broker", "replica:10911"))
                .hasMessageContaining("not registered");
        verify(admin, never()).cleanExpiredConsumerQueueByAddr(anyString());
    }

    @Test
    void missingTopicMetadataIsNotAnEmptySafeRetentionSet() throws Exception {
        when(admin.getAllTopicConfig("master:10911", 5000)).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("cleanup cannot be reviewed");
        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("registry is unavailable");
        assertThatThrownBy(() -> service.preview("instance-a", "", "master:10911"))
                .hasMessageContaining("required");
    }

    @Test
    void timeoutAndNegativeAcknowledgementAreUncertainWithoutAutomaticRetry() throws Exception {
        doThrow(new org.apache.rocketmq.remoting.exception.RemotingTimeoutException("master:10911", 5000))
                .when(admin).cleanUnusedTopicByAddr("master:10911");
        assertThat(service.apply(request(Operation.UNUSED_TOPIC_QUEUES)).status()).isEqualTo("UNKNOWN");
        verify(admin).cleanUnusedTopicByAddr("master:10911");
        when(admin.cleanExpiredConsumerQueueByAddr("master:10911")).thenReturn(false);
        assertThat(service.apply(request(Operation.EXPIRED_CONSUME_QUEUES)).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void auditFailureCannotChangeACompletedCleanupReceipt() {
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        assertThat(service.apply(request(Operation.UNUSED_TOPIC_QUEUES)).status()).isEqualTo("BROKER_REPORTED_COMPLETION");
    }

    @Test
    void previewAndPreflightInterruptionsRestoreFlagAndDoNotDelete() throws Exception {
        var request = request(Operation.UNUSED_TOPIC_QUEUES);
        doThrow(new InterruptedException()).when(admin).examineBrokerClusterInfo();
        try {
            assertThatThrownBy(this::preview).hasMessageContaining("preview was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("preflight was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).cleanUnusedTopicByAddr(anyString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedCleanupPreservesTheUnknownResultAndThreadFlag() throws Exception {
        doThrow(new InterruptedException()).when(admin).cleanUnusedTopicByAddr(anyString());
        try {
            assertThat(service.apply(request(Operation.UNUSED_TOPIC_QUEUES)).status()).isEqualTo("UNKNOWN");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void controllerExposesReadOnlyScopeAndExplicitCleanupCommand() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokerQueueCleanupController(service)).build();
        mvc.perform(get("/api/brokers/queue-cleanup").param("instanceId", "instance-a")
                        .param("brokerName", "broker-a").param("address", "master:10911"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configuredTopics[0]").value("orders"));
        mvc.perform(post("/api/brokers/queue-cleanup").contentType("application/json")
                        .content(new ObjectMapper().writeValueAsString(request(Operation.EXPIRED_CONSUME_QUEUES))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BROKER_REPORTED_COMPLETION"));
    }
}
