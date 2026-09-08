/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
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
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Outcome;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Preview;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Queue;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Request;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

class ConsumerOffsetCopyServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final TopicRouteData route = new TopicRouteData();
    private final ClusterInfo cluster = new ClusterInfo();
    private final TopicStatsTable ranges = new TopicStatsTable();
    private final ConsumeStats source = new ConsumeStats();
    private final ConsumeStats target = new ConsumeStats();
    private ConsumerOffsetCopyService service;

    private MessageQueue queue(int id) { return new MessageQueue("orders", "broker-a", id); }

    private OffsetWrapper value(long offset) {
        var value = new OffsetWrapper();
        value.setConsumerOffset(offset);
        return value;
    }

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new ConsumerOffsetCopyService(new RuntimeAdminClientResolver(repository, factory,
                new MqAdminProperties(), mock(MqClientPool.class)), new OperationAuditService(audits));
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        var data = new QueueData();
        data.setBrokerName("broker-a");
        data.setReadQueueNums(2);
        route.setQueueDatas(new ArrayList<>(List.of(data)));
        cluster.setBrokerAddrTable(new HashMap<>());
        cluster.getBrokerAddrTable().put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(java.util.Map.of(0L, "master:10911", 1L, "replica:10911"))));
        for (int id = 0; id < 2; id++) {
            var range = new TopicOffset();
            range.setMinOffset(10);
            range.setMaxOffset(Long.MAX_VALUE);
            ranges.getOffsetTable().put(queue(id), range);
            source.getOffsetTable().put(queue(id), value(9007199254740993L + id));
            target.getOffsetTable().put(queue(id), value(20 + id));
        }
        when(admin.examineTopicRouteInfo("orders")).thenReturn(route);
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        when(admin.examineTopicStats("master:10911", "orders")).thenReturn(ranges);
        when(admin.examineConsumeStats("master:10911", "source", "orders", 5000)).thenReturn(source);
        when(admin.examineConsumeStats("master:10911", "target", "orders", 5000)).thenReturn(target);
        when(admin.examineSubscriptionGroupConfig(eq("master:10911"), anyString()))
                .thenReturn(new SubscriptionGroupConfig());
        when(admin.examineConsumerConnectionInfo(anyString(), eq("master:10911")))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"));
        doAnswer(invocation -> {
            target.getOffsetTable().put(invocation.getArgument(2), value(invocation.getArgument(3)));
            return null;
        }).when(admin).updateConsumeOffset(eq("master:10911"), eq("target"), any(), anyLong());
    }

    private Preview preview() { return service.preview("instance-a", "orders", "source", "target"); }

    private Request request() {
        return new Request("instance-a", "orders", "source", "target",
                preview().queues().stream().map(Queue::expected).toList());
    }

    @Test
    void copiesReviewedQueuesPreciselyToRegisteredMasterAndRecordsAudit() throws Exception {
        var request = request();
        assertThat(request.expected().getFirst().sourceOffset()).isEqualTo("9007199254740993");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        var receipt = service.apply(request);
        assertThat(receipt.queues()).extracting(Outcome::status).containsExactly(Status.CONFIRMED, Status.CONFIRMED);
        assertThat(receipt.queues().getFirst().observedOffset()).isEqualTo("9007199254740993");
        verify(admin).updateConsumeOffset("master:10911", "target", queue(0), 9007199254740993L);
        verify(audits, times(2)).insert(any(RmqOperationAudit.class));
    }

    @Test
    void missingTargetIsExplicitAndCanBeInitialized() {
        target.getOffsetTable().clear();
        assertThat(preview().queues().getFirst().expected().targetOffset()).isNull();
        assertThat(service.apply(request()).queues()).allMatch(row -> row.status() == Status.CONFIRMED);
    }

    @Test
    void doesNotWriteAlreadyEqualOffsets() throws Exception {
        target.getOffsetTable().putAll(source.getOffsetTable());
        assertThat(service.apply(request()).queues()).allMatch(row -> row.status() == Status.UNCHANGED);
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void changedSourceOrTargetInvalidatesPreview() throws Exception {
        var request = request();
        target.getOffsetTable().put(queue(0), value(30));
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("changed");
        Request sourceRequest = request();
        source.getOffsetTable().put(queue(0), value(40));
        assertThatThrownBy(() -> service.apply(sourceRequest)).hasMessageContaining("changed");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void retentionAdvanceBlocksApply() {
        var request = request();
        ranges.getOffsetTable().get(queue(0)).setMinOffset(9007199254740994L);
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("retained range");
    }

    @Test
    void producerAppendDoesNotInvalidatePreviewButReadableQueueChangeDoes() throws Exception {
        ranges.getOffsetTable().get(queue(0)).setMaxOffset(9007199254740993L);
        var request = request();
        ranges.getOffsetTable().get(queue(0)).setMaxOffset(Long.MAX_VALUE);
        assertThat(service.apply(request).queues().getFirst().status()).isEqualTo(Status.CONFIRMED);
        var secondRequest = request();
        route.getQueueDatas().getFirst().setReadQueueNums(1);
        assertThatThrownBy(() -> service.apply(secondRequest)).hasMessageContaining("changed");
    }

    @Test
    void keepsConfirmedEarlierQueueWhenLaterWriteFails() throws Exception {
        doThrow(new MQBrokerException(ResponseCode.SYSTEM_ERROR, "write failed"))
                .when(admin).updateConsumeOffset("master:10911", "target", queue(1), 9007199254740994L);
        assertThat(service.apply(request()).queues()).extracting(Outcome::status)
                .containsExactly(Status.CONFIRMED, Status.UNKNOWN);
        assertThat(target.getOffsetTable().get(queue(0)).getConsumerOffset()).isEqualTo(9007199254740993L);
        assertThat(target.getOffsetTable().get(queue(1)).getConsumerOffset()).isEqualTo(21);
    }

    @Test
    void failedWriteStopsRemainingQueuesWithoutRetryOrFalseFailureClaim() throws Exception {
        doThrow(new org.apache.rocketmq.remoting.exception.RemotingTimeoutException("master:10911", 5000))
                .when(admin).updateConsumeOffset("master:10911", "target", queue(0), 9007199254740993L);
        assertThat(service.apply(request()).queues()).extracting(Outcome::status)
                .containsExactly(Status.UNKNOWN, Status.NOT_ATTEMPTED);
        verify(admin, times(1)).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void mismatchedReadBackStopsAndRetainsObservedValue() throws Exception {
        doNothing().when(admin).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        var receipt = service.apply(request());
        assertThat(receipt.queues().getFirst().observedOffset()).isEqualTo("20");
        assertThat(receipt.queues()).extracting(Outcome::status).containsExactly(Status.UNKNOWN, Status.NOT_ATTEMPTED);
    }

    @Test
    void failedReadBackAndAuditSinkDoNotDiscardReceiptAfterWrite() throws Exception {
        var request = request();
        when(admin.examineConsumeStats("master:10911", "target", "orders", 5000))
                .thenReturn(target).thenThrow(new MQBrokerException(ResponseCode.SYSTEM_ERROR, "readback failed"));
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        assertThat(service.apply(request).queues().getFirst().status()).isEqualTo(Status.UNKNOWN);
        assertThat(target.getOffsetTable().get(queue(0)).getConsumerOffset()).isEqualTo(9007199254740993L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "target"})
    void onlineGroupBlocksEveryWrite(String group) throws Exception {
        var connection = new ConsumerConnection();
        connection.getConnectionSet().add(new Connection());
        doReturn(connection).when(admin).examineConsumerConnectionInfo(group, "master:10911");
        assertThatThrownBy(this::preview).hasMessageContaining("Stop both");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void onlyExplicitOfflineResponseCodesAreAccepted() throws Exception {
        doThrow(new MQBrokerException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"))
                .when(admin).examineConsumerConnectionInfo(anyString(), anyString());
        assertThat(preview().queues()).hasSize(2);
        doThrow(new MQBrokerException(ResponseCode.NO_PERMISSION, "not online"))
                .when(admin).examineConsumerConnectionInfo(anyString(), anyString());
        assertThatThrownBy(this::preview).hasMessageContaining("not online");
        doThrow(new MQClientException(ResponseCode.SYSTEM_ERROR, "not online"))
                .when(admin).examineConsumerConnectionInfo(anyString(), anyString());
        assertThatThrownBy(this::preview).hasMessageContaining("not online");
    }

    @Test
    void missingGroupMasterAndUnknownConnectionsBlockPreview() throws Exception {
        doReturn(null).when(admin).examineSubscriptionGroupConfig("master:10911", "target");
        assertThatThrownBy(this::preview).hasMessageContaining("already exist");
        doReturn(new SubscriptionGroupConfig()).when(admin).examineSubscriptionGroupConfig("master:10911", "target");
        doReturn(null).when(admin).examineConsumerConnectionInfo("target", "master:10911");
        assertThatThrownBy(this::preview).hasMessageContaining("state is unavailable");
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        assertThatThrownBy(this::preview).hasMessageContaining("registered master");
    }

    @Test
    void missingOrExpiredSourceOffsetBlocksCopy() {
        source.getOffsetTable().remove(queue(0));
        assertThatThrownBy(this::preview).hasMessageContaining("Source offset");
        source.getOffsetTable().put(queue(0), value(-1));
        assertThatThrownBy(this::preview).hasMessageContaining("Source offset");
        source.getOffsetTable().put(queue(0), value(9));
        assertThatThrownBy(this::preview).hasMessageContaining("Source offset");
        source.getOffsetTable().put(queue(0), value(Long.MAX_VALUE));
        assertThat(preview().queues().getFirst().expected().sourceOffset()).isEqualTo(Long.toString(Long.MAX_VALUE));
    }

    @Test
    void invalidRequestsAreRejectedBeforeAdminUse() {
        for (String topic : List.of("", "%RETRY%source", "%DLQ%source")) {
            assertThatThrownBy(() -> service.preview("instance-a", topic, "source", "target"))
                    .hasMessageContaining("normal topic");
        }
        assertThatThrownBy(() -> service.preview("instance-a", "orders", "source", "source"))
                .hasMessageContaining("different");
        assertThatThrownBy(() -> service.apply(new Request("instance-a", "orders", "source", "target", null)))
                .hasMessageContaining("reviewed");
    }

    @Test
    void interruptedWriteReturnsUncertainReceiptAndRestoresInterruptFlag() throws Exception {
        doThrow(new InterruptedException()).when(admin).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        try {
            assertThat(service.apply(request()).queues().getFirst().status()).isEqualTo(Status.UNKNOWN);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsMissingAndDuplicateTopologyBeforeAnyWrite() throws Exception {
        var request = request();
        route.getQueueDatas().add(route.getQueueDatas().getFirst());
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("duplicate topic route");
        route.getQueueDatas().clear();
        assertThatThrownBy(this::preview).hasMessageContaining("unavailable");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void rejectsMissingRangesAndOffsetsAndNoReadableQueues() throws Exception {
        var request = request();
        ranges.getOffsetTable().remove(queue(0));
        assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("range is invalid or missing");
        doReturn(null).when(admin).examineTopicStats("master:10911", "orders");
        assertThatThrownBy(this::preview).hasMessageContaining("retention metadata");
        doReturn(ranges).when(admin).examineTopicStats("master:10911", "orders");
        doReturn(null).when(admin).examineConsumeStats("master:10911", "source", "orders", 5000);
        assertThatThrownBy(this::preview).hasMessageContaining("offset metadata");
        route.getQueueDatas().getFirst().setReadQueueNums(0);
        assertThatThrownBy(this::preview).hasMessageContaining("no readable queues");
    }

    @Test
    void interruptsDuringPreviewAndPreflightRestoreFlagWithoutWriting() throws Exception {
        var request = request();
        doThrow(new InterruptedException()).when(admin).examineTopicRouteInfo("orders");
        try {
            assertThatThrownBy(this::preview).hasMessageContaining("inspection was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            assertThatThrownBy(() -> service.apply(request)).hasMessageContaining("preflight was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void controllerPreservesStringOffsetsAndReadsBeforeWriting() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ConsumerOffsetCopyController(service)).build();
        mvc.perform(get("/api/consumer-offset-copy").param("instanceId", "instance-a")
                        .param("topic", "orders").param("sourceGroup", "source").param("targetGroup", "target"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.queues[0].expected.sourceOffset")
                        .value("9007199254740993"));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(post("/api/consumer-offset-copy").contentType("application/json")
                        .content(mapper.writeValueAsString(request())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.queues[0].status").value("CONFIRMED"));
    }
}
