/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageRequestMode;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Outcome;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Preview;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Request;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumerRequestModeServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final MessageRequestModeReader reader = mock(MessageRequestModeReader.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Properties config = new Properties();
    private final Map<String, JsonNode> stored = new HashMap<>();
    private final ClusterInfo cluster = new ClusterInfo();
    private final TopicRouteData route = new TopicRouteData();
    private ConsumerRequestModeService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new ConsumerRequestModeService(new RuntimeAdminClientResolver(repository, factory,
                new MqAdminProperties(), mock(MqClientPool.class)), reader, new OperationAuditService(audits));
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        cluster.setBrokerAddrTable(new HashMap<>());
        route.setQueueDatas(new ArrayList<>());
        for (String name : List.of("broker-b", "broker-a")) {
            cluster.getBrokerAddrTable().put(name, new BrokerData("cluster-a", name,
                    new HashMap<>(Map.of(0L, name + ":10911"))));
            var data = new QueueData();
            data.setBrokerName(name);
            route.getQueueDatas().add(data);
        }
        config.setProperty("defaultMessageRequestMode", "PULL");
        config.setProperty("defaultPopShareQueueNum", "-1");
        config.setProperty("serverLoadBalancerEnable", "true");
        when(admin.getBrokerConfig(anyString())).thenReturn(config);
        when(admin.examineTopicRouteInfo("orders")).thenReturn(route);
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        when(admin.examineSubscriptionGroupConfig(anyString(), eq("group-a"))).thenReturn(new SubscriptionGroupConfig());
        when(admin.examineConsumerConnectionInfo(eq("group-a"), anyString()))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"));
        when(reader.read(eq(admin), anyString(), eq("orders"), eq("group-a")))
                .thenAnswer(invocation -> stored.get(invocation.getArgument(1)));
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(0), override(invocation.<MessageRequestMode>getArgument(3).name(),
                    invocation.getArgument(4)));
            return null;
        }).when(admin).setMessageRequestMode(anyString(), eq("orders"), eq("group-a"), any(), anyInt(), eq(5000L));
    }

    private JsonNode override(String mode, int sharing) {
        return mapper.createObjectNode().put("topic", "orders").put("consumerGroup", "group-a")
                .put("mode", mode).put("popShareQueueNum", sharing);
    }

    private Preview preview() { return service.preview("instance-a", "orders", "group-a"); }
    private Request request(String mode, int sharing) {
        return new Request("instance-a", "orders", "group-a", mode, sharing, preview().brokers());
    }

    @Test
    void previewsEffectiveDefaultsAndOverridesWithoutWriting() throws Exception {
        stored.put("broker-b:10911", override("POP", 2));
        var result = preview();
        assertThat(result.brokers()).hasSize(2);
        assertThat(result.brokers().getFirst().mode()).isEqualTo("PULL");
        assertThat(result.brokers().getFirst().popShareQueueNum()).isZero();
        assertThat(result.brokers().getFirst().explicit()).isFalse();
        assertThat(result.brokers().getLast().mode()).isEqualTo("POP");
        assertThat(result.brokers().getLast().popShareQueueNum()).isEqualTo(2);
        assertThat(result.brokers().getLast().explicit()).isTrue();
        verify(admin, never()).setMessageRequestMode(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void inheritedPopUsesDefaultSharingAndWritesAnExplicitOverride() {
        config.setProperty("defaultMessageRequestMode", "POP");
        var before = preview().brokers().getFirst();
        assertThat(before.popShareQueueNum()).isEqualTo(-1);
        assertThat(before.explicit()).isFalse();
        assertThat(service.apply(request("POP", -1)).brokers()).allMatch(row -> "CONFIRMED".equals(row.status()));
        assertThat(preview().brokers()).allMatch(ConsumerRequestMode.Broker::explicit);
    }

    @Test
    void appliesToEveryTopicMasterAndRecordsEachAttempt() throws Exception {
        var receipt = service.apply(request("POP", 3));
        assertThat(receipt.brokers()).extracting(Outcome::status).containsExactly("CONFIRMED", "CONFIRMED");
        verify(admin).setMessageRequestMode("broker-a:10911", "orders", "group-a", MessageRequestMode.POP, 3, 5000);
        verify(admin).setMessageRequestMode("broker-b:10911", "orders", "group-a", MessageRequestMode.POP, 3, 5000);
        verify(audits, times(2)).insert(any(RmqOperationAudit.class));
    }

    @Test
    void unchangedExplicitValuesAreNotWritten() throws Exception {
        stored.put("broker-a:10911", override("PULL", 0));
        stored.put("broker-b:10911", override("PULL", 0));
        assertThat(service.apply(request("PULL", 0)).brokers()).allMatch(row -> "UNCHANGED".equals(row.status()));
        verify(admin, never()).setMessageRequestMode(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void changedDefaultsOrOverridesInvalidateTheReviewedPreview() throws Exception {
        var request = request("POP", -1);
        stored.put("broker-b:10911", override("PULL", 0));
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("changed");
        verify(admin, never()).setMessageRequestMode(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void aTimeoutStopsFurtherBrokersAndDoesNotRetry() throws Exception {
        doThrow(new org.apache.rocketmq.remoting.exception.RemotingTimeoutException("broker-a:10911", 5000))
                .when(admin).setMessageRequestMode(eq("broker-a:10911"), anyString(), anyString(), any(), anyInt(), anyLong());
        assertThat(service.apply(request("POP", -1)).brokers()).extracting(Outcome::status)
                .containsExactly("UNKNOWN", "NOT_ATTEMPTED");
        verify(admin, times(1)).setMessageRequestMode(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void partialSuccessAndObservedMismatchRemainInTheReceipt() throws Exception {
        doNothing().when(admin).setMessageRequestMode(eq("broker-b:10911"), anyString(), anyString(), any(), anyInt(), anyLong());
        var result = service.apply(request("POP", -1));
        assertThat(result.brokers()).extracting(Outcome::status).containsExactly("CONFIRMED", "UNKNOWN");
        assertThat(result.brokers().getLast().observed().mode()).isEqualTo("PULL");
    }

    @Test
    void auditFailureCannotDiscardConfirmedBrokerUpdates() {
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        assertThat(service.apply(request("POP", -1)).brokers()).allMatch(row -> "CONFIRMED".equals(row.status()));
    }

    @Test
    void onlineOrUnknownConsumerStateBlocksPreflight() throws Exception {
        var connection = new ConsumerConnection();
        connection.getConnectionSet().add(new Connection());
        doReturn(connection).when(admin).examineConsumerConnectionInfo(eq("group-a"), anyString());
        assertThatThrownBy(this::preview).hasMessageContaining("Stop the consumer");
        doReturn(null).when(admin).examineConsumerConnectionInfo(eq("group-a"), anyString());
        assertThatThrownBy(this::preview).hasMessageContaining("connection state");
    }

    @Test
    void onlyOfflineResponseCodesPermitChanges() throws Exception {
        doThrow(new MQBrokerException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"))
                .when(admin).examineConsumerConnectionInfo(eq("group-a"), anyString());
        assertThat(preview().brokers()).hasSize(2);
        doThrow(new MQBrokerException(ResponseCode.NO_PERMISSION, "denied"))
                .when(admin).examineConsumerConnectionInfo(eq("group-a"), anyString());
        assertThatThrownBy(this::preview).hasMessageContaining("denied");
        doThrow(new MQClientException(ResponseCode.SYSTEM_ERROR, "failed"))
                .when(admin).examineConsumerConnectionInfo(eq("group-a"), anyString());
        assertThatThrownBy(this::preview).hasMessageContaining("failed");
    }

    @Test
    void missingGroupMasterAndRoutePreventIncompleteScope() throws Exception {
        when(admin.examineSubscriptionGroupConfig(anyString(), anyString())).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("must exist");
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().clear();
        assertThatThrownBy(this::preview).hasMessageContaining("registered master");
        route.getQueueDatas().clear();
        assertThatThrownBy(this::preview).hasMessageContaining("no registered brokers");
        when(admin.examineTopicRouteInfo("orders")).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("unavailable");
    }

    @Test
    void malformedStoredOverridesAreErrorsNotInheritedDefaults() {
        stored.put("broker-a:10911", mapper.createObjectNode().put("mode", "POP"));
        assertThatThrownBy(this::preview).hasMessageContaining("Stored request mode is invalid");
        stored.put("broker-a:10911", override("UNSUPPORTED", 0));
        assertThatThrownBy(this::preview).hasMessageContaining("must be POP or PULL");
    }

    @Test
    void invalidModesSharingAndSystemTopicsAreRejected() {
        assertThatThrownBy(() -> service.preview("instance-a", "%RETRY%group-a", "group-a"))
                .hasMessageContaining("normal topic");
        assertThatThrownBy(() -> service.apply(new Request("instance-a", "orders", "group-a", "OTHER", 0, List.of())))
                .hasMessageContaining("must be POP or PULL");
        assertThatThrownBy(() -> service.apply(new Request("instance-a", "orders", "group-a", "PULL", 1, List.of())))
                .hasMessageContaining("valid POP sharing");
        assertThatThrownBy(() -> service.apply(new Request("instance-a", "orders", "group-a", "POP", -2, List.of())))
                .hasMessageContaining("valid POP sharing");
    }

    @Test
    void interruptedWriteStopsFurtherBrokersAndRestoresFlag() throws Exception {
        doThrow(new InterruptedException()).when(admin).setMessageRequestMode(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
        try {
            assertThat(service.apply(request("POP", -1)).brokers().getFirst().status()).isEqualTo("UNKNOWN");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void previewAndPreflightInterruptionsRestoreFlagBeforeAnyWrite() throws Exception {
        var request = request("POP", -1);
        doThrow(new InterruptedException()).when(admin).examineTopicRouteInfo("orders");
        try {
            assertThatThrownBy(this::preview).hasMessageContaining("inspection was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("preflight was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).setMessageRequestMode(anyString(), anyString(), anyString(), any(), anyInt(), anyLong());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void missingDefaultsAndInvalidRouteEntriesCannotProduceAPreview() throws Exception {
        when(admin.getBrokerConfig(anyString())).thenReturn(null);
        assertThatThrownBy(this::preview).hasMessageContaining("defaults are unavailable");
        route.getQueueDatas().getFirst().setBrokerName(null);
        assertThatThrownBy(this::preview).hasMessageContaining("invalid broker");
    }

    @Test
    void controllerBindsPreviewAndConfirmedChanges() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ConsumerRequestModeController(service)).build();
        mvc.perform(get("/api/consumer-request-mode").param("instanceId", "instance-a")
                        .param("topic", "orders").param("group", "group-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.brokers[0].explicit").value(false));
        mvc.perform(post("/api/consumer-request-mode").contentType("application/json")
                        .content(mapper.writeValueAsString(request("POP", -1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.brokers[0].status").value("CONFIRMED"));
    }
}
