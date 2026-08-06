/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class RocketMQBrokerConfigServiceTest {

    @Mock
    private DefaultMQAdminExt adminExt;
    @Mock
    private AuditService auditService;

    private RocketMQBrokerConfigService brokerConfigService;

    @BeforeEach
    void setUp() {
        brokerConfigService = new RocketMQBrokerConfigService(adminExt, auditService);
    }

    @Test
    void updateSucceedsWhenAuditRecordingFails() throws Exception {
        Properties config = new Properties();
        config.setProperty("flushDiskType", "ASYNC_FLUSH");
        doNothing().when(adminExt).updateBrokerConfig("broker-a:10911", config);
        doThrow(new IllegalStateException("audit db down")).when(auditService)
                .record(anyString(), anyString(), anyString(), anyString());

        brokerConfigService.updateBrokerConfig("broker-a:10911", "cluster-a", config);
    }

    @Test
    void updatePreservesBrokerFailureWhenAuditRecordingFails() throws Exception {
        Properties config = new Properties();
        doThrow(new IllegalStateException("broker unavailable")).when(adminExt)
                .updateBrokerConfig("broker-a:10911", config);
        doThrow(new IllegalStateException("audit db down")).when(auditService)
                .record(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> brokerConfigService.updateBrokerConfig("broker-a:10911", "cluster-a", config))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to update broker config: broker unavailable");
    }
}
