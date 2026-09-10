/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQBrokerConfigServiceTest {

    @Mock
    private MqAdminExtFactory adminFactory;
    @Mock
    private RocketMQProperties properties;
    @Mock
    private DefaultMQAdminExt adminExt;
    @Mock
    private AuditService auditService;
    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    private RocketMQBrokerConfigService brokerConfigService;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        lenient().when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        brokerConfigService = new RocketMQBrokerConfigService(
                adminFactory, properties, runtimeAdminClientResolver, auditService);
    }

    @Test
    void updateSucceedsWhenAuditRecordingFails() throws Exception {
        Properties config = new Properties();
        config.setProperty("flushDiskType", "ASYNC_FLUSH");
        doNothing().when(adminExt).updateBrokerConfig("broker-a:10911", config);
        doThrow(new IllegalStateException("audit db down")).when(auditService)
                .record(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        brokerConfigService.updateBrokerConfig("broker-a:10911", "cluster-a", config);
    }

    @Test
    void updatePreservesBrokerFailureWhenAuditRecordingFails() throws Exception {
        Properties config = new Properties();
        doThrow(new IllegalStateException("broker unavailable")).when(adminExt)
                .updateBrokerConfig("broker-a:10911", config);
        doThrow(new IllegalStateException("audit db down")).when(auditService)
                .record(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> brokerConfigService.updateBrokerConfig("broker-a:10911", "cluster-a", config))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to update broker config: broker unavailable");
    }

    @Test
    void updateRecordsStructuredClusterId() throws Exception {
        Properties config = new Properties();
        doNothing().when(adminExt).updateBrokerConfig("broker-a:10911", config);

        brokerConfigService.updateBrokerConfig("broker-a:10911", "cluster-a", config);

        verify(auditService).record(
                "UPDATE_BROKER_CONFIG", "BROKER", "CLUSTER:cluster-a", "cluster-a",
                "brokerAddr=broker-a:10911, config={}", "SUCCESS");
    }

    @Test
    void trimsFlushDiskTypeWithoutChangingUnknownValueFallback() throws Exception {
        Properties padded = new Properties();
        padded.setProperty("flushDiskType", " SYNC_FLUSH ");
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(padded);

        assertThat(brokerConfigService.getBrokerConfig("broker-a:10911").getFlushDiskType())
                .isEqualTo(FlushDiskType.SYNC_FLUSH);

        Properties unknown = new Properties();
        unknown.setProperty("flushDiskType", "future-mode");
        when(adminExt.getBrokerConfig("broker-b:10911")).thenReturn(unknown);

        assertThat(brokerConfigService.getBrokerConfig("broker-b:10911").getFlushDiskType())
                .isEqualTo(FlushDiskType.ASYNC_FLUSH);
    }

    @Test
    void getBrokerConfigWithInstanceIdDelegatesToRuntimeResolver() throws Exception {
        Properties props = new Properties();
        props.setProperty("flushDiskType", "SYNC_FLUSH");
        props.setProperty("maxMessageSize", "2097152");
        when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(adminExt));
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(props);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("broker-a:10911", "inst-1");

        verify(runtimeAdminClientResolver).execute(eq("inst-1"), any());
        assertThat(vo.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(vo.getMaxMessageSize()).isEqualTo(2097152);
    }

    @Test
    void getBrokerConfigMapsEveryPropertyIntoVO() throws Exception {
        Properties props = new Properties();
        props.setProperty("flushDiskType", "SYNC_FLUSH");
        props.setProperty("autoCreateTopicEnable", "false");
        props.setProperty("autoCreateSubscriptionGroup", "false");
        props.setProperty("maxMessageSize", "8388608");
        props.setProperty("defaultTopicQueueNums", "16");
        props.setProperty("fileReservedTime", "48");
        props.setProperty("brokerPermission", "4");
        props.setProperty("deleteWhen", "03");
        props.setProperty("msgTraceTopicName", "TRACE_A");
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(props);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("broker-a:10911");

        assertThat(vo.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(vo.isAutoCreateTopicEnable()).isFalse();
        assertThat(vo.isAutoCreateSubscriptionGroup()).isFalse();
        assertThat(vo.getMaxMessageSize()).isEqualTo(8388608);
        assertThat(vo.getWriteQueueNums()).isEqualTo(16);
        assertThat(vo.getReadQueueNums()).isEqualTo(16);
        assertThat(vo.getFileReservedTime()).isEqualTo(48);
        assertThat(vo.getBrokerPermission()).isEqualTo(4);
        assertThat(vo.getDeleteWhen()).isEqualTo("03");
        assertThat(vo.getMsgTraceTopicName()).isEqualTo("TRACE_A");
    }

    @Test
    void getBrokerConfigFallsBackToDefaultsForMissingOrMalformedValues() throws Exception {
        when(adminExt.getBrokerConfig("empty-broker:10911")).thenReturn(new Properties());

        ClusterConfigVO defaults = brokerConfigService.getBrokerConfig("empty-broker:10911");
        assertThat(defaults.getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
        assertThat(defaults.isAutoCreateTopicEnable()).isTrue();
        assertThat(defaults.isAutoCreateSubscriptionGroup()).isTrue();
        assertThat(defaults.getMaxMessageSize()).isEqualTo(4194304);
        assertThat(defaults.getWriteQueueNums()).isEqualTo(8);
        assertThat(defaults.getReadQueueNums()).isEqualTo(8);
        assertThat(defaults.getFileReservedTime()).isEqualTo(72);
        assertThat(defaults.getBrokerPermission()).isEqualTo(6);
        assertThat(defaults.getDeleteWhen()).isEqualTo("04");
        assertThat(defaults.getMsgTraceTopicName()).isEqualTo("RMQ_SYS_TRACE_TOPIC");

        Properties malformed = new Properties();
        malformed.setProperty("maxMessageSize", "not-a-number");
        malformed.setProperty("defaultTopicQueueNums", "   ");
        malformed.setProperty("flushDiskType", "fancy-mode");
        when(adminExt.getBrokerConfig("malformed-broker:10911")).thenReturn(malformed);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("malformed-broker:10911");
        assertThat(vo.getMaxMessageSize()).isEqualTo(4194304);
        assertThat(vo.getWriteQueueNums()).isEqualTo(8);
        assertThat(vo.getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
    }
}
