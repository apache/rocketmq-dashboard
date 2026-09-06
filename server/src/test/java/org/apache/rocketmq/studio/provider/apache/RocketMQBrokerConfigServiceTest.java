/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
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
import org.mockito.ArgumentCaptor;
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
    void mapsBrokerPropertiesOntoTheClusterConfigVoTest() throws Exception {
        Properties config = new Properties();
        config.setProperty("flushDiskType", "SYNC_FLUSH");
        config.setProperty("autoCreateTopicEnable", "false");
        config.setProperty("autoCreateSubscriptionGroup", "false");
        config.setProperty("maxMessageSize", "8388608");
        config.setProperty("defaultTopicQueueNums", "16");
        config.setProperty("fileReservedTime", "168");
        config.setProperty("brokerPermission", "6");
        config.setProperty("deleteWhen", "06");
        config.setProperty("msgTraceTopicName", "TRACE_ORDER");
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(config);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("broker-a:10911");

        assertThat(vo.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(vo.isAutoCreateTopicEnable()).isFalse();
        assertThat(vo.isAutoCreateSubscriptionGroup()).isFalse();
        assertThat(vo.getMaxMessageSize()).isEqualTo(8388608);
        assertThat(vo.getWriteQueueNums()).isEqualTo(16);
        assertThat(vo.getReadQueueNums()).isEqualTo(16);
        assertThat(vo.getFileReservedTime()).isEqualTo(168);
        assertThat(vo.getBrokerPermission()).isEqualTo(6);
        assertThat(vo.getDeleteWhen()).isEqualTo("06");
        assertThat(vo.getMsgTraceTopicName()).isEqualTo("TRACE_ORDER");
    }

    @Test
    void fillsDefaultsForMissingOrMalformedBrokerPropertiesTest() throws Exception {
        Properties sparse = new Properties();
        sparse.setProperty("flushDiskType", "not-a-mode");
        sparse.setProperty("defaultTopicQueueNums", "many");
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(sparse);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("broker-a:10911");

        assertThat(vo.getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
        assertThat(vo.isAutoCreateTopicEnable()).isTrue();
        assertThat(vo.isAutoCreateSubscriptionGroup()).isTrue();
        assertThat(vo.getMaxMessageSize()).isEqualTo(4194304);
        assertThat(vo.getWriteQueueNums()).isEqualTo(8);
        assertThat(vo.getReadQueueNums()).isEqualTo(8);
        assertThat(vo.getFileReservedTime()).isEqualTo(72);
        assertThat(vo.getBrokerPermission()).isEqualTo(6);
        assertThat(vo.getDeleteWhen()).isEqualTo("04");
        assertThat(vo.getMsgTraceTopicName()).isEqualTo("RMQ_SYS_TRACE_TOPIC");
    }

    @Test
    void getBrokerConfigSurfacesBrokerFailuresAsBusinessErrorsTest() throws Exception {
        when(adminExt.getBrokerConfig("broker-a:10911"))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> brokerConfigService.getBrokerConfig("broker-a:10911"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to get broker config")
                .hasMessageContaining("connection refused");
    }

    @Test
    void getBrokerConfigRoutesThroughTheRuntimeResolverWithInstanceIdTest() throws Exception {
        ClusterConfigVO remote = new ClusterConfigVO();
        when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<ClusterConfigVO>>getArgument(1)
                        .apply(adminExt));
        Properties config = new Properties();
        config.setProperty("flushDiskType", "SYNC_FLUSH");
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(config);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("broker-a:10911", "instance-x");

        verify(runtimeAdminClientResolver).execute(eq("instance-x"), any());
        assertThat(vo.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
    }

    @Test
    void updateRecordsFailedAuditWhenTheBrokerRejectsTheConfigTest() throws Exception {
        Properties config = new Properties();
        config.setProperty("flushDiskType", "ASYNC_FLUSH");
        doThrow(new IllegalStateException("broker unavailable")).when(adminExt)
                .updateBrokerConfig("broker-a:10911", config);

        assertThatThrownBy(() -> brokerConfigService.updateBrokerConfig(
                "broker-a:10911", "cluster-a", config))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq("UPDATE_BROKER_CONFIG"), eq("BROKER"),
                eq("CLUSTER:cluster-a"), eq("cluster-a"), detailCaptor.capture(), eq("FAILED"));
        assertThat(detailCaptor.getValue()).contains("error=broker unavailable");
    }

    @Test
    void updateRejectsMissingNameServerConfigurationTest() {
        when(properties.getNamesrvAddr()).thenReturn("   ");

        assertThatThrownBy(() -> brokerConfigService.updateBrokerConfig(
                "broker-a:10911", "cluster-a", new Properties()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Set studio.rocketmq.namesrv-addr");
    }
}
