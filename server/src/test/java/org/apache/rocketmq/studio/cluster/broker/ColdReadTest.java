/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.cluster.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.GlobalExceptionHandler;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ColdReadTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MqAdminExtFactory factory = new MqAdminExtFactory() {
        @Override
        protected DefaultMQAdminExt newAdmin(RPCHook hook) {
            return admin;
        }
    };
    private final ClusterInfo cluster = new ClusterInfo();
    private ColdReadService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        when(instances.findByIdentifier("instance-a")).thenReturn(Optional.of(InstanceVO.builder()
                .name("instance-a").vendor(InstanceVendor.APACHE).endpoint("selected:9876").build()));
        var resolver = new RuntimeAdminClientResolver(instances, factory, new MqAdminProperties(), new MqClientPool());
        service = new ColdReadService(resolver, mapper, new OperationAuditService(audits));
        cluster.setBrokerAddrTable(new HashMap<>(Map.of("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "master:10911", 1L, "replica:10911"))))));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        Properties configuration = new Properties();
        configuration.setProperty("coldDataFlowControlEnable", "false");
        configuration.setProperty("coldCtrStrategyEnable", "true");
        when(admin.getBrokerConfig("master:10911")).thenReturn(configuration);
        when(admin.getColdDataFlowCtrInfo("master:10911")).thenReturn("""
                {"runtimeTable":{"runtime-only":{"coldAcc":23,"lastColdReadTimeMills":1788825600000},
                  "orders":{"coldAcc":9007199254740993,"lastColdReadTimeMills":1788825600001}},
                 "configTable":{"orders":9007199254740995,"orders||adaptive":1000,"config-only":2000,
                  "adaptive-only||adaptive":3000},
                 "cgColdReadThreshold":100000,"globalColdReadThreshold":1000000,"globalAcc":9007199254740997}
                """);
        mvc = MockMvcBuilders.standaloneSetup(new ColdReadController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        factory.shutdown();
        Thread.interrupted();
    }

    @Test
    void httpMergesConfiguredRuntimeAndAdaptiveGroupsWithoutLosingPrecision() throws Exception {
        mvc.perform(get("/api/brokers/cold-read").param("instanceId", "instance-a").param("brokerName", " broker-a "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("master:10911"))
                .andExpect(jsonPath("$.data.enabled").value("false"))
                .andExpect(jsonPath("$.data.globalBytes").value("9007199254740997"))
                .andExpect(jsonPath("$.data.groups.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].name").value("adaptive-only"))
                .andExpect(jsonPath("$.data.groups[0].adaptiveThreshold").value("3000"))
                .andExpect(jsonPath("$.data.groups[0].coldBytes").doesNotExist())
                .andExpect(jsonPath("$.data.groups[2].configuredThreshold").value("9007199254740995"))
                .andExpect(jsonPath("$.data.groups[2].coldBytes").value("9007199254740993"))
                .andExpect(jsonPath("$.data.groups[3].lastReadMillis").value("1788825600000"));
        verify(admin).setNamesrvAddr("selected:9876");
        verify(admin, never()).getColdDataFlowCtrInfo("replica:10911");
        verifyNoInteractions(audits);
    }

    @Test
    void setSendsOneExactPropertyAndVerifiesTheBrokerResult() throws Exception {
        var command = command(ColdReadCommand.Action.SET, "9007199254740995");
        mvc.perform(post("/api/brokers/cold-read/config").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.threshold").value("9007199254740995"));
        var properties = ArgumentCaptor.forClass(Properties.class);
        verify(admin).updateColdDataFlowCtrGroupConfig(org.mockito.ArgumentMatchers.eq("master:10911"),
                properties.capture());
        assertThat(properties.getValue()).containsOnly(Map.entry("orders", "9007199254740995"));
        var audit = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getDetail()).contains("verified=true", "address=master:10911", "orders");
        assertThat(audit.getValue().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void removeOnlyDeletesTheAdminOverrideAndVerifiesAbsence() throws Exception {
        when(admin.getColdDataFlowCtrInfo("master:10911")).thenReturn(
                "{\"runtimeTable\":{},\"configTable\":{\"orders||adaptive\":200}}");
        var result = service.change(command(ColdReadCommand.Action.REMOVE, null));
        assertThat(result.verified()).isTrue();
        assertThat(result.threshold()).isNull();
        verify(admin).removeColdDataFlowCtrGroupConfig("master:10911", "orders");
        verify(admin, never()).updateColdDataFlowCtrGroupConfig(anyString(), any());
    }

    @Test
    void acknowledgedButUnappliedLimitIsNotReportedAsVerified() {
        var result = service.change(command(ColdReadCommand.Action.SET, "5000"));
        assertThat(result.verified()).isFalse();
        assertThat(result.verificationError()).contains("read-back differs");
    }

    @Test
    void failedReadBackDoesNotTurnAcknowledgedWriteIntoRetryableFailure() throws Exception {
        when(admin.getColdDataFlowCtrInfo("master:10911")).thenThrow(new IllegalStateException("unavailable"));
        var result = service.change(command(ColdReadCommand.Action.SET, "5000"));
        assertThat(result.verified()).isFalse();
        assertThat(result.verificationError()).contains("acknowledged", "Refresh before retrying");
        verify(admin).updateColdDataFlowCtrGroupConfig(anyString(), any());
    }

    @Test
    void interruptedReadBackRetainsTheAcknowledgementAndInterruptFlag() throws Exception {
        when(admin.getColdDataFlowCtrInfo("master:10911")).thenThrow(new InterruptedException("cancelled"));
        assertThat(service.change(command(ColdReadCommand.Action.SET, "5000")).verified()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void auditFailureDoesNotChangeVerifiedMutation() {
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        assertThat(service.change(command(ColdReadCommand.Action.SET, "9007199254740995")).verified()).isTrue();
    }

    @Test
    void mutationFailureIsAuditedWithoutReadBack() throws Exception {
        doThrow(new IllegalStateException("write denied")).when(admin)
                .updateColdDataFlowCtrGroupConfig(anyString(), any());
        assertThatThrownBy(() -> service.change(command(ColdReadCommand.Action.SET, "5000")))
                .hasMessageContaining("write denied");
        verify(admin, never()).getColdDataFlowCtrInfo(anyString());
        var audit = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getResult()).isEqualTo("FAILED");
    }

    @Test
    void mutationInterruptionRestoresTheFlag() throws Exception {
        doThrow(new InterruptedException("cancelled")).when(admin)
                .removeColdDataFlowCtrGroupConfig(anyString(), anyString());
        assertThatThrownBy(() -> service.change(command(ColdReadCommand.Action.REMOVE, null)))
                .hasMessageContaining("cancelled");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "1.5", " 10 ", "01", "9223372036854775808"})
    void invalidThresholdCannotReachBroker(String threshold) {
        assertThatThrownBy(() -> service.change(command(ColdReadCommand.Action.SET, threshold)))
                .hasMessageContaining("positive 64-bit integer");
        verifyNoInteractions(admin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "orders\nother", "orders||adaptive"})
    void invalidOrReservedGroupCannotReachBroker(String group) {
        var command = new ColdReadCommand("instance-a", "broker-a", group, ColdReadCommand.Action.SET, "1");
        assertThatThrownBy(() -> service.change(command)).hasMessageContaining("consumer group name");
        verifyNoInteractions(admin);
    }

    @Test
    void nullGroupActionAndUnexpectedRemoveThresholdAreRejected() {
        assertThatThrownBy(() -> service.change(new ColdReadCommand("instance-a", "broker-a",
                null, ColdReadCommand.Action.SET, "1"))).hasMessageContaining("consumer group name");
        assertThatThrownBy(() -> service.change(command(null, null))).hasMessage("action is required");
        assertThatThrownBy(() -> service.change(command(ColdReadCommand.Action.REMOVE, "1")))
                .hasMessageContaining("must not include");
        verifyNoInteractions(admin);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-json", "null", "{}", "{\"runtimeTable\":[],\"configTable\":{}}",
        "{\"runtimeTable\":{},\"configTable\":{\"orders\":1.5}}",
        "{\"runtimeTable\":{},\"configTable\":{\"orders\":9223372036854775808}}"})
    void absentMalformedOrUnsupportedProtocolDoesNotBecomeHealthyZero(String json) throws Exception {
        when(admin.getColdDataFlowCtrInfo("master:10911")).thenReturn(json);
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a")).hasMessageContaining("Broker returned");
    }

    @Test
    void emptyTablesAndNullCountersRemainUnreported() throws Exception {
        when(admin.getColdDataFlowCtrInfo("master:10911")).thenReturn(
                "{\"runtimeTable\":{},\"configTable\":{},\"globalAcc\":null}");
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.groups()).isEmpty();
        assertThat(result.defaultThreshold()).isNull();
        assertThat(result.globalBytes()).isNull();
    }

    @Test
    void missingBrokerConfigurationCannotImplyFlowControlEnabled() throws Exception {
        when(admin.getBrokerConfig("master:10911")).thenReturn(null);
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a")).hasMessageContaining("no configuration");
    }

    @Test
    void unknownBrokerAndMissingMasterDoNotProbeArbitraryAddresses() throws Exception {
        assertThatThrownBy(() -> service.inspect("instance-a", "outside:10911"))
                .hasMessageContaining("not registered");
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a")).hasMessageContaining("no registered master");
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().put(0L, " ");
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a")).hasMessageContaining("no registered master");
        verify(admin, never()).getColdDataFlowCtrInfo(anyString());
    }

    @Test
    void missingInstanceOrBrokerAndInvalidHttpCommandAreRejected() throws Exception {
        mvc.perform(get("/api/brokers/cold-read").param("brokerName", "broker-a"))
                .andExpect(status().isBadRequest());
        for (String name : new String[] {null, "", " "}) {
            assertThatThrownBy(() -> service.inspect("instance-a", name)).hasMessage("brokerName is required");
        }
        mvc.perform(post("/api/brokers/cold-read/config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceId\":\"instance-a\",\"group\":\"orders\"}"))
                .andExpect(status().isBadRequest());
    }

    private ColdReadCommand command(ColdReadCommand.Action action, String threshold) {
        return new ColdReadCommand("instance-a", "broker-a", "orders", action, threshold);
    }
}
