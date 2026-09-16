/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.QueueTimeSpan;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.apache.rocketmq.studio.instance.group.ConsumerTimeSpanSnapshot.CursorState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumerTimeSpanServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final MessageQueue queue = new MessageQueue("orders", "broker-a", 2);
    private final TopicStatsTable stats = new TopicStatsTable();
    private final ConsumeStats offsets = new ConsumeStats();
    private final TopicOffset range = new TopicOffset();
    private final OffsetWrapper offset = new OffsetWrapper();
    private final QueueTimeSpan span = new QueueTimeSpan();
    private ConsumerTimeSpanService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) {
                return admin;
            }
        };
        var resolver = new RuntimeAdminClientResolver(repository, factory, new MqAdminProperties(),
                mock(MqClientPool.class));
        service = new ConsumerTimeSpanService(resolver);
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        range.setMinOffset(10);
        range.setMaxOffset(100);
        stats.getOffsetTable().put(queue, range);
        offset.setConsumerOffset(42);
        offset.setBrokerOffset(100);
        offsets.getOffsetTable().put(queue, offset);
        span.setMessageQueue(queue);
        span.setMinTimeStamp(1700000000000L);
        span.setMaxTimeStamp(1700000100000L);
        span.setConsumeTimeStamp(1700000042000L);
        span.setDelayTime(Long.MAX_VALUE);
        when(admin.examineTopicStats("orders")).thenReturn(stats);
        when(admin.examineConsumeStats("group-a", "orders")).thenReturn(offsets);
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(List.of(span));
    }

    private ConsumerTimeSpanSnapshot inspect() {
        return service.inspect("instance-a", "orders", "group-a");
    }

    @Test
    void mapsRetainedTimesAndRecordedOffsetReferenceWithoutUsingUnreliableDelay() throws Exception {
        var snapshot = inspect();
        assertThat(snapshot.topic()).isEqualTo("orders");
        assertThat(snapshot.group()).isEqualTo("group-a");
        assertThat(snapshot.sampledAt()).isNotNull();
        assertThat(snapshot.queues()).hasSize(1);
        var row = snapshot.queues().getFirst();
        assertThat(row.earliestTime()).isEqualTo("1700000000000");
        assertThat(row.latestTime()).isEqualTo("1700000100000");
        assertThat(row.cursorTime()).isEqualTo("1700000042000");
        assertThat(row.cursorState()).isEqualTo(CursorState.RECORDED_OFFSET_REFERENCE);
        assertThat(row.spanAvailable()).isTrue();
        verify(admin).setNamesrvAddr("ns-a:9876");
        verify(admin).queryConsumeTimeSpan("orders", "group-a");
    }

    @Test
    void doesNotTreatTheBrokersEarliestFallbackAsAConsumptionRecord() {
        offset.setConsumerOffset(0);
        span.setConsumeTimeStamp(span.getMinTimeStamp());
        var row = inspect().queues().getFirst();
        assertThat(row.cursorState()).isEqualTo(CursorState.EARLIEST_MESSAGE_FALLBACK);
        assertThat(row.cursorTime()).isEqualTo(row.earliestTime());
    }

    @Test
    void marksOffsetOutsideRetentionEvenIfBrokerReportsAPositiveReference() {
        offset.setConsumerOffset(10);
        assertThat(inspect().queues().getFirst().cursorState()).isEqualTo(CursorState.OUTSIDE_RETAINED_RANGE);
        offset.setConsumerOffset(101);
        assertThat(inspect().queues().getFirst().cursorState()).isEqualTo(CursorState.OUTSIDE_RETAINED_RANGE);
        offset.setConsumerOffset(100);
        assertThat(inspect().queues().getFirst().cursorState()).isEqualTo(CursorState.RECORDED_OFFSET_REFERENCE);
    }

    @Test
    void distinguishesMissingOffsetsMissingSpansAndMissingTimestamps() throws Exception {
        offsets.getOffsetTable().clear();
        assertThat(inspect().queues().getFirst().cursorState()).isEqualTo(CursorState.OFFSET_UNAVAILABLE);
        offset.setConsumerOffset(-1);
        offsets.getOffsetTable().put(queue, offset);
        assertThat(inspect().queues().getFirst().consumerOffset()).isNull();
        offset.setConsumerOffset(42);
        span.setMinTimeStamp(-1);
        span.setMaxTimeStamp(0);
        span.setConsumeTimeStamp(-1);
        var row = inspect().queues().getFirst();
        assertThat(row.earliestTime()).isNull();
        assertThat(row.latestTime()).isNull();
        assertThat(row.cursorState()).isEqualTo(CursorState.TIMESTAMP_UNAVAILABLE);
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(List.of());
        row = inspect().queues().getFirst();
        assertThat(row.spanAvailable()).isFalse();
        assertThat(row.cursorTime()).isNull();
    }

    @Test
    void preservesLargeOffsetsAndSortsBrokerAndQueueNames() throws Exception {
        range.setMaxOffset(9007199254740995L);
        offset.setConsumerOffset(9007199254740993L);
        var other = new MessageQueue("orders", "broker-a", 1);
        stats.getOffsetTable().put(other, range);
        var snapshot = inspect();
        assertThat(snapshot.queues()).extracting(ConsumerTimeSpanSnapshot.Queue::queueId).containsExactly(1, 2);
        assertThat(snapshot.queues().getLast().consumerOffset()).isEqualTo("9007199254740993");
        assertThat(snapshot.queues().getLast().maxOffset()).isEqualTo("9007199254740995");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void requiresTopicAndGroupBeforeBrokerReads(String missing) throws Exception {
        assertThatThrownBy(() -> service.inspect("instance-a", missing, "group-a")).hasMessageContaining("required");
        assertThatThrownBy(() -> service.inspect("instance-a", "orders", missing)).hasMessageContaining("required");
        verify(admin, never()).examineTopicStats("orders");
    }

    @Test
    void rejectsMissingTopLevelMetadata() throws Exception {
        when(admin.examineTopicStats("orders")).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("metadata");
        when(admin.examineTopicStats("orders")).thenReturn(stats);
        stats.setOffsetTable(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("metadata");
    }

    @Test
    void rejectsMissingConsumerOffsetsOrTimeSpans() throws Exception {
        when(admin.examineConsumeStats("group-a", "orders")).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("metadata");
        when(admin.examineConsumeStats("group-a", "orders")).thenReturn(offsets);
        offsets.setOffsetTable(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("metadata");
    }

    @Test
    void rejectsNullTimeSpanList() throws Exception {
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("metadata");
    }

    @Test
    void rejectsDuplicateUnknownAndNullTimeSpanEntries() throws Exception {
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(List.of(span, span));
        assertThatThrownBy(this::inspect).hasMessageContaining("topology changed");
        span.setMessageQueue(new MessageQueue("other", "broker-a", 2));
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(List.of(span));
        assertThatThrownBy(this::inspect).hasMessageContaining("topology changed");
        span.setMessageQueue(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("topology changed");
        var list = new ArrayList<QueueTimeSpan>();
        list.add(null);
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(list);
        assertThatThrownBy(this::inspect).hasMessageContaining("topology changed");
    }

    @Test
    void rejectsMalformedQueueRanges() {
        range.setMinOffset(-1);
        assertThatThrownBy(this::inspect).hasMessageContaining("invalid queue metadata");
        range.setMinOffset(101);
        assertThatThrownBy(this::inspect).hasMessageContaining("invalid queue metadata");
    }

    @Test
    void rejectsMalformedQueueIdentity() throws Exception {
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenReturn(List.of());
        for (MessageQueue invalid : new MessageQueue[] {
            new MessageQueue("wrong", "broker-a", 0),
            new MessageQueue("orders", "", 0),
            new MessageQueue("orders", "broker-a", -1)
        }) {
            stats.getOffsetTable().clear();
            stats.getOffsetTable().put(invalid, range);
            assertThatThrownBy(this::inspect).hasMessageContaining("invalid queue metadata");
        }
    }

    @Test
    void preservesInterruptFlagAndReportsBrokerErrors() throws Exception {
        when(admin.queryConsumeTimeSpan("orders", "group-a")).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(this::inspect).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        doThrow(new IllegalStateException("broker offline")).when(admin).queryConsumeTimeSpan("orders", "group-a");
        assertThatThrownBy(this::inspect).hasMessageContaining("broker offline");
    }

    @Test
    void supportsHttpContractWithStringOffsetsAndExplicitState() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ConsumerTimeSpanController(service)).build();
        mvc.perform(get("/api/consumer-time-spans").param("instanceId", "instance-a")
                .param("topic", "orders").param("group", "group-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queues[0].consumerOffset").value("42"))
                .andExpect(jsonPath("$.data.queues[0].cursorState").value("RECORDED_OFFSET_REFERENCE"))
                .andExpect(jsonPath("$.data.queues[0].earliestTime").value("1700000000000"));
        mvc.perform(get("/api/consumer-time-spans").param("instanceId", "instance-a"))
                .andExpect(status().isBadRequest());
    }
}
