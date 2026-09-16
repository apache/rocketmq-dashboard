/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumeQueueData;
import org.apache.rocketmq.remoting.protocol.body.QueryConsumeQueueResponseBody;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumeQueueServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private ConsumeQueueService service;
    private ClusterInfo cluster;
    private BrokerData broker;
    private TopicStatsTable stats;
    private TopicOffset range;
    private QueryConsumeQueueResponseBody body;

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
        service = new ConsumeQueueService(resolver);
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        cluster = new ClusterInfo();
        cluster.setBrokerAddrTable(new HashMap<>());
        broker = new BrokerData("cluster-a", "broker-a", new HashMap<>());
        broker.getBrokerAddrs().put(0L, "master-a:10911");
        cluster.getBrokerAddrTable().put("broker-a", broker);
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        stats = new TopicStatsTable();
        range = new TopicOffset();
        range.setMinOffset(0);
        range.setMaxOffset(100);
        stats.getOffsetTable().put(new MessageQueue("orders", "broker-a", 2), range);
        when(admin.examineTopicStats("orders")).thenReturn(stats);
        body = new QueryConsumeQueueResponseBody();
        body.setMinQueueIndex(0);
        body.setMaxQueueIndex(100);
        body.setQueueData(List.of(entry(false)));
        when(admin.queryConsumeQueue(eq("master-a:10911"), eq("orders"), eq(2),
                anyLong(), anyInt(), any())).thenReturn(body);
    }

    private ConsumeQueueData entry(boolean extension) {
        var data = new ConsumeQueueData();
        data.setPhysicOffset(9007199254740993L);
        data.setPhysicSize(128);
        data.setTagsCode(-9007199254740993L);
        if (extension) {
            data.setExtendDataJson("{\"tagsCode\":12}");
            data.setBitMap("1010");
        }
        return data;
    }

    private ConsumeQueueSnapshot inspect(String index, String group) {
        return service.inspect("instance-a", "orders", "broker-a", 2, index, 16, group);
    }

    @Test
    void preservesPrecisionAndDoesNotInventLogicalOffsetsOrFilterResults() throws Exception {
        var snapshot = inspect("42", null);
        assertThat(snapshot.entries()).hasSize(1);
        var entry = snapshot.entries().getFirst();
        assertThat(entry.physicalOffset()).isEqualTo("9007199254740993");
        assertThat(entry.tagsCode()).isEqualTo("-9007199254740993");
        assertThat(entry.ordinal()).isEqualTo(1);
        assertThat(entry.indexMatch()).isNull();
        verify(admin).setNamesrvAddr("ns-a:9876");
        verify(admin).queryConsumeQueue("master-a:10911", "orders", 2, 42L, 16, null);
    }

    @Test
    void mapsOnlyActuallyEvaluatedExtensionResults() {
        var subscription = new SubscriptionData("orders", "amount > 10");
        subscription.setExpressionType("SQL92");
        body.setSubscriptionData(subscription);
        body.setFilterData("{\"bloomFilterData\":{}}");
        var pass = entry(true);
        pass.setEval(true);
        var reject = entry(true);
        var missing = entry(false);
        missing.setMsg("Cq extend not exist!addr: -1");
        body.setQueueData(List.of(pass, reject, missing));
        var snapshot = inspect("42", " group-a ");
        assertThat(snapshot.expressionType()).isEqualTo("SQL92");
        assertThat(snapshot.expression()).isEqualTo("amount > 10");
        assertThat(snapshot.filterData()).contains("bloomFilterData");
        assertThat(snapshot.entries()).extracting(ConsumeQueueSnapshot.Entry::indexMatch)
                .containsExactly(true, false, null);
        assertThat(snapshot.entries().getFirst().bitmap()).isEqualTo("1010");
        assertThat(snapshot.entries().getFirst().extension()).contains("tagsCode");
        assertThat(snapshot.entries().getLast().message()).contains("not exist");
    }

    @Test
    void unavailableSubscriptionAndOmittedGroupCannotReportMatch() {
        body.setQueueData(List.of(entry(true)));
        body.setFilterData("group-a@orders is not online!");
        assertThat(inspect("42", "group-a").entries().getFirst().indexMatch()).isNull();
        body.setSubscriptionData(new SubscriptionData("orders", "*"));
        assertThat(inspect("42", " ").entries().getFirst().indexMatch()).isNull();
    }

    @Test
    void handlesExactQueueEndWithoutCallingTheBodylessBrokerResponse() throws Exception {
        range.setMaxOffset(9007199254740993L);
        var snapshot = inspect("9007199254740993", null);
        assertThat(snapshot.atEnd()).isTrue();
        assertThat(snapshot.entries()).isEmpty();
        assertThat(snapshot.maxIndex()).isEqualTo("9007199254740993");
        verify(admin, never()).queryConsumeQueue(any(), any(), anyInt(), anyLong(), anyInt(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-1", "1.1", " 1", "1e3", "9223372036854775808", "11111111111111111111"})
    void rejectsInvalidIndexBeforeBrokerAccess(String index) throws Exception {
        assertThatThrownBy(() -> inspect(index, null)).isInstanceOf(BusinessException.class);
        verify(admin, never()).examineBrokerClusterInfo();
    }

    @Test
    void validatesRequiredTargetAndBoundedSpan() {
        assertThatThrownBy(() -> service.inspect("instance-a", "", "broker-a", 2, "0", 16, null))
                .hasMessageContaining("required");
        assertThatThrownBy(() -> service.inspect("instance-a", "orders", "", 2, "0", 16, null))
                .hasMessageContaining("required");
        assertThatThrownBy(() -> service.inspect("instance-a", "orders", "broker-a", -1, "0", 16, null))
                .hasMessageContaining("required");
        for (int count : new int[] {0, 33}) {
            assertThatThrownBy(() -> service.inspect("instance-a", "orders", "broker-a", 2, "0", count, null))
                    .hasMessageContaining("required");
        }
    }

    @Test
    void rejectsUnavailableMastersIncludingMissingTopology() throws Exception {
        broker.getBrokerAddrs().clear();
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Registered master");
        broker.setBrokerAddrs(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Registered master");
        cluster.getBrokerAddrTable().clear();
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Registered master");
        cluster.setBrokerAddrTable(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Registered master");
        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Registered master");
        verify(admin, never()).examineTopicStats(any());
    }

    @Test
    void rejectsQueuesOutsideTopicAndInvalidStats() throws Exception {
        stats.getOffsetTable().clear();
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Queue not found");
        stats.setOffsetTable(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Queue not found");
        when(admin.examineTopicStats("orders")).thenReturn(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("Queue not found");
    }

    @Test
    void distinguishesRetentionRangeErrorsFromInvalidBrokerRanges() {
        range.setMinOffset(10);
        assertThatThrownBy(() -> inspect("9", null)).hasMessageContaining("[10, 100]");
        assertThatThrownBy(() -> inspect("101", null)).hasMessageContaining("[10, 100]");
        range.setMaxOffset(9);
        assertThatThrownBy(() -> inspect("10", null)).hasMessageContaining("invalid queue range");
        range.setMinOffset(-1);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("invalid queue range");
    }

    @Test
    void rejectsUnavailableAndMalformedSnapshots() throws Exception {
        body.setQueueData(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("valid ConsumeQueue");
        body.setQueueData(List.of());
        body.setMinQueueIndex(-1);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("valid ConsumeQueue");
        body.setMinQueueIndex(101);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("valid ConsumeQueue");
        when(admin.queryConsumeQueue(any(), any(), anyInt(), anyLong(), anyInt(), isNull())).thenReturn(null);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("valid ConsumeQueue");
    }

    @Test
    void rejectsNullOrExcessiveEntries() {
        var entries = new ArrayList<ConsumeQueueData>();
        entries.add(null);
        body.setQueueData(entries);
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("invalid or excessive");
        body.setQueueData(java.util.Collections.nCopies(17, entry(false)));
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("invalid or excessive");
    }

    @Test
    void preservesInterruptionAndReportsRpcFailures() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        doThrow(new IllegalStateException("broker offline")).when(admin).examineBrokerClusterInfo();
        assertThatThrownBy(() -> inspect("0", null)).hasMessageContaining("broker offline");
    }

    @Test
    void httpContractReturnsExactStringsAndNullableEvaluation() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConsumeQueueController(service)).build();
        mvc.perform(get("/api/messages/consume-queue").param("instanceId", "instance-a")
                .param("topic", "orders").param("brokerName", "broker-a").param("queueId", "2")
                .param("index", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedIndex").value("42"))
                .andExpect(jsonPath("$.data.entries[0].physicalOffset").value("9007199254740993"))
                .andExpect(jsonPath("$.data.entries[0].physicalSize").value(128))
                .andExpect(jsonPath("$.data.entries[0].indexMatch").isEmpty());
        mvc.perform(get("/api/messages/consume-queue").param("instanceId", "instance-a"))
                .andExpect(status().isBadRequest());
    }
}
