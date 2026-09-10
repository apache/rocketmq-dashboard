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
    void configParsingFallsBackOnMissingAndMalformedNumericValues() throws Exception {
        Properties malformed = new Properties();
        malformed.setProperty("defaultTopicQueueNums", "abc");
        when(adminExt.getBrokerConfig("broker-a:10911")).thenReturn(malformed);

        ClusterConfigVO vo = brokerConfigService.getBrokerConfig("broker-a:10911");
        assertThat(vo.getWriteQueueNums()).isEqualTo(8);
        assertThat(vo.getReadQueueNums()).isEqualTo(8);
        assertThat(vo.getMaxMessageSize()).isEqualTo(4194304);

        Properties valid = new Properties();
        valid.setProperty("defaultTopicQueueNums", "16");
        valid.setProperty("maxMessageSize", "5242880");
        when(adminExt.getBrokerConfig("broker-b:10911")).thenReturn(valid);
        ClusterConfigVO parsed = brokerConfigService.getBrokerConfig("broker-b:10911");
        assertThat(parsed.getWriteQueueNums()).isEqualTo(16);
        assertThat(parsed.getMaxMessageSize()).isEqualTo(5242880);
    }
}
