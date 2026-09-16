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
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerReadAheadServiceTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final Properties config = new Properties();
    private final ClusterInfo cluster = new ClusterInfo();
    private BrokerReadAheadService service;

    @BeforeEach
    void setUp() throws Exception {
        var factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) { return admin; }
        };
        service = new BrokerReadAheadService(new RuntimeAdminClientResolver(repository, factory,
                new MqAdminProperties(), mock(MqClientPool.class)), new OperationAuditService(audits));
        when(repository.findByIdentifier("instance-a")).thenReturn(Optional.of(
                InstanceVO.builder().endpoint("ns-a:9876").vendor(InstanceVendor.APACHE).build()));
        cluster.setBrokerAddrTable(new HashMap<>());
        cluster.getBrokerAddrTable().put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "master:10911", 1L, "replica:10911"))));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        config.setProperty("dataReadAheadEnable", "true");
        config.setProperty("secretKey", "not-in-response");
        when(admin.getBrokerConfig(anyString())).thenReturn(config);
        when(admin.setCommitLogReadAheadMode(anyString(), anyString())).thenAnswer(invocation -> {
            config.setProperty("dataReadAheadEnable", "0".equals(invocation.getArgument(1)) ? "true" : "false");
            return null;
        });
    }

    private BrokerReadAheadService.Snapshot inspect() { return service.inspect("instance-a", "broker-a", "master:10911"); }
    private BrokerReadAheadService.Request request(boolean before, boolean enabled) {
        return new BrokerReadAheadService.Request("instance-a", "broker-a", "master:10911", before, enabled);
    }

    @Test
    void readsOnlyTheRuntimeSettingFromTheRegisteredNode() throws Exception {
        assertThat(inspect().enabled()).isTrue();
        assertThat(inspect().address()).isEqualTo("master:10911");
        assertThat(service.inspect("instance-a", "broker-a", "replica:10911").enabled()).isTrue();
        verify(admin, never()).setCommitLogReadAheadMode(anyString(), anyString());
    }

    @Test
    void mapsNativeModesAndConfirmsConfigurationWithoutClaimingOsSuccess() throws Exception {
        var disabled = service.apply(request(true, false));
        assertThat(disabled.status()).isEqualTo("CONFIG_CONFIRMED");
        assertThat(disabled.acknowledged()).isTrue();
        assertThat(disabled.before().enabled()).isTrue();
        assertThat(disabled.observed().enabled()).isFalse();
        verify(admin).setCommitLogReadAheadMode("master:10911", "1");
        assertThat(service.apply(request(false, true)).observed().enabled()).isTrue();
        verify(admin).setCommitLogReadAheadMode("master:10911", "0");
    }

    @Test
    void stalePreviewAndUnregisteredAddressesNeverWrite() throws Exception {
        config.setProperty("dataReadAheadEnable", "false");
        assertThatThrownBy(() -> service.apply(request(true, false))).hasMessageContaining("changed");
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a", "unregistered:10911"))
                .hasMessageContaining("not registered");
        cluster.getBrokerAddrTable().clear();
        assertThatThrownBy(() -> service.apply(request(false, true))).hasMessageContaining("not registered");
        verify(admin, never()).setCommitLogReadAheadMode(anyString(), anyString());
    }

    @Test
    void unchangedRequestDoesNotSendAnOsAdviceScan() throws Exception {
        assertThat(service.apply(request(true, true)).status()).isEqualTo("UNCHANGED");
        verify(admin, never()).setCommitLogReadAheadMode(anyString(), anyString());
    }

    @Test
    void failedWriteIsUncertainAndNotRetried() throws Exception {
        doThrow(new MQBrokerException(ResponseCode.SYSTEM_ERROR, "failed"))
                .when(admin).setCommitLogReadAheadMode(anyString(), anyString());
        var result = service.apply(request(true, false));
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.acknowledged()).isFalse();
        assertThat(result.observed()).isNull();
        verify(admin).setCommitLogReadAheadMode("master:10911", "1");
    }

    @Test
    void anAcknowledgementWithoutAChangedConfigurationIsNotConfirmed() throws Exception {
        doReturn("accepted").when(admin).setCommitLogReadAheadMode(anyString(), anyString());
        var result = service.apply(request(true, false));
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.acknowledged()).isTrue();
        assertThat(result.observed().enabled()).isTrue();
    }

    @Test
    void failedReadbackRetainsAcknowledgementAndAuditFailureDoesNotChangeReceipt() throws Exception {
        when(admin.getBrokerConfig("master:10911")).thenReturn(config)
                .thenThrow(new MQBrokerException(ResponseCode.NO_PERMISSION, "denied"));
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        var result = service.apply(request(true, false));
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.acknowledged()).isTrue();
        assertThat(result.observed()).isNull();
    }

    @Test
    void missingAndUnknownValuesAreNotAssumedDisabled() throws Exception {
        config.remove("dataReadAheadEnable");
        assertThatThrownBy(this::inspect).hasMessageContaining("supported dataReadAheadEnable");
        config.setProperty("dataReadAheadEnable", "unsupported");
        assertThatThrownBy(this::inspect).hasMessageContaining("supported dataReadAheadEnable");
        when(admin.getBrokerConfig(anyString())).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("supported dataReadAheadEnable");
    }

    @Test
    void requiredFieldsAndRegistryFailuresBlockBeforeWriting() throws Exception {
        assertThatThrownBy(() -> service.inspect("instance-a", "", "master:10911")).hasMessageContaining("required");
        assertThatThrownBy(() -> service.apply(new BrokerReadAheadService.Request(
                "instance-a", "broker-a", "master:10911", null, true))).hasMessageContaining("reviewed");
        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        assertThatThrownBy(this::inspect).hasMessageContaining("registry is unavailable");
        verify(admin, never()).setCommitLogReadAheadMode(anyString(), anyString());
    }

    @Test
    void previewAndPreflightInterruptionsRestoreFlagWithoutWriting() throws Exception {
        doThrow(new InterruptedException()).when(admin).examineBrokerClusterInfo();
        try {
            assertThatThrownBy(this::inspect).hasMessageContaining("inspection was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            assertThatThrownBy(() -> service.apply(request(true, false))).hasMessageContaining("preflight was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).setCommitLogReadAheadMode(anyString(), anyString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedWriteReturnsUncertaintyAndPreservesInterruptFlag() throws Exception {
        doThrow(new InterruptedException()).when(admin).setCommitLogReadAheadMode(anyString(), anyString());
        try {
            assertThat(service.apply(request(true, false)).status()).isEqualTo("UNKNOWN");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void controllerReadsAndWritesWithoutReturningUnrelatedConfiguration() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokerReadAheadController(service)).build();
        var response = mvc.perform(get("/api/brokers/read-ahead").param("instanceId", "instance-a")
                        .param("brokerName", "broker-a").param("address", "master:10911"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secretKey", "not-in-response");
        mvc.perform(post("/api/brokers/read-ahead").contentType("application/json")
                        .content(new ObjectMapper().writeValueAsString(request(true, false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIG_CONFIRMED"));
    }
}
