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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.common.exception.GlobalExceptionHandler;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerTimerTest {
    private final RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
    private final MQAdminExt admin = mock(MQAdminExt.class);
    private final BrokerTimerService service = new BrokerTimerService(resolver);
    private final ClusterInfo cluster = new ClusterInfo();
    private final Properties configuration = new Properties();
    private final KVTable runtime = new KVTable();
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(call -> call.<MqAdminExtFactory.AdminAction<?>>getArgument(1).apply(admin))
                .when(resolver).execute(anyString(), any());
        cluster.setBrokerAddrTable(new HashMap<>(Map.of("broker-a",
                new BrokerData("cluster-a", "broker-a", new HashMap<>(Map.of(
                        0L, "master:10911", 1L, "replica:10911"))))));
        configuration.setProperty("timerWheelEnable", "true");
        configuration.setProperty("timerStopEnqueue", "false");
        configuration.setProperty("timerPrecisionMs", "1000");
        configuration.setProperty("timerMaxDelaySec", "604800");
        runtime.setTable(new HashMap<>(Map.of("timerReadBehind", "7",
                "timerOffsetBehind", "9007199254740993", "timerCongestNum", "9007199254740995",
                "timerEnqueueTps", "1.5", "timerDequeueTps", "0.5")));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        when(admin.getBrokerConfig("master:10911")).thenReturn(configuration);
        when(admin.fetchBrokerRuntimeStats("master:10911")).thenReturn(runtime);
        mvc = MockMvcBuilders.standaloneSetup(new BrokerTimerController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void httpSnapshotUsesRegisteredMasterAndPreservesExactValues() throws Exception {
        configuration.setProperty("accessKey", "must-not-be-exported");
        configuration.setProperty("storePathRootDir", "/private/broker");
        runtime.getTable().put("unrelatedRuntimeField", "private-value");
        mvc.perform(get("/api/brokers/timer").param("instanceId", "instance-a")
                        .param("brokerName", " broker-a "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("master:10911"))
                .andExpect(jsonPath("$.data.brokerName").value("broker-a"))
                .andExpect(jsonPath("$.data.sampledAt").isNumber())
                .andExpect(jsonPath("$.data.configuration.values.timerPrecisionMs").value("1000"))
                .andExpect(jsonPath("$.data.runtime.values.timerReadBehind").value("7"))
                .andExpect(jsonPath("$.data.runtime.values.timerOffsetBehind").value("9007199254740993"))
                .andExpect(jsonPath("$.data.runtime.values.timerCongestNum").value("9007199254740995"))
                .andExpect(jsonPath("$.data.runtime.values.timerEnqueueTps").value("1.5"))
                .andExpect(jsonPath("$.data.configuration.values.accessKey").doesNotExist())
                .andExpect(jsonPath("$.data.configuration.values.storePathRootDir").doesNotExist())
                .andExpect(jsonPath("$.data.runtime.values.unrelatedRuntimeField").doesNotExist());
        verify(resolver).execute(eq("instance-a"), any());
        verify(admin, never()).getBrokerConfig("replica:10911");
        verify(admin, never()).fetchBrokerRuntimeStats("replica:10911");
    }

    @Test
    void disabledTimerWithZeroMetricsRemainsDisabled() {
        configuration.setProperty("timerWheelEnable", "false");
        runtime.getTable().replaceAll((key, value) -> "0");
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.configuration().values()).containsEntry("timerWheelEnable", "false");
        assertThat(result.runtime().values()).containsEntry("timerReadBehind", "0");
    }

    @Test
    void unsupportedFieldsAreAbsentRatherThanInventedDefaults() {
        configuration.clear();
        runtime.getTable().clear();
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.configuration().values()).isEmpty();
        assertThat(result.runtime().values()).isEmpty();
        assertThat(result.runtime().error()).isNull();
    }

    @Test
    void failedConfigurationDoesNotHideRuntime() throws Exception {
        when(admin.getBrokerConfig("master:10911")).thenThrow(new IllegalStateException("Config denied"));
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.configuration().error()).isEqualTo("Config denied");
        assertThat(result.configuration().values()).isEmpty();
        assertThat(result.runtime().values()).containsEntry("timerReadBehind", "7");
    }

    @Test
    void failedRuntimeDoesNotHideConfiguration() throws Exception {
        when(admin.fetchBrokerRuntimeStats("master:10911")).thenThrow(new IllegalStateException("Runtime denied"));
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.runtime().error()).isEqualTo("Runtime denied");
        assertThat(result.configuration().values()).containsEntry("timerWheelEnable", "true");
    }

    @Test
    void nullPayloadsProduceSourceErrors() throws Exception {
        when(admin.getBrokerConfig("master:10911")).thenReturn(null);
        when(admin.fetchBrokerRuntimeStats("master:10911")).thenReturn(null);
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.configuration().error()).contains("no configuration");
        assertThat(result.runtime().error()).contains("no runtime statistics");
    }

    @Test
    void nullRuntimeTableProducesSourceError() {
        runtime.setTable(null);
        assertThat(service.inspect("instance-a", "broker-a").runtime().error()).contains("no runtime statistics");
    }

    @Test
    void exceptionWithoutMessageStillMarksSourceUnavailable() throws Exception {
        when(admin.getBrokerConfig("master:10911")).thenThrow(new IllegalStateException());
        assertThat(service.inspect("instance-a", "broker-a").configuration().error()).isEqualTo("IllegalStateException");
    }

    @Test
    void interruptedConfigurationStopsTheNextProbe() throws Exception {
        when(admin.getBrokerConfig("master:10911")).thenThrow(new InterruptedException("cancelled"));
        try {
            assertThatThrownBy(() -> service.inspect("instance-a", "broker-a")).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).fetchBrokerRuntimeStats(anyString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unknownBrokerCannotSupplyAnArbitraryAddress() throws Exception {
        mvc.perform(get("/api/brokers/timer").param("instanceId", "instance-a")
                        .param("brokerName", "outside:10911"))
                .andExpect(status().isNotFound());
        verify(admin, never()).getBrokerConfig(anyString());
    }

    @Test
    void absentMasterDoesNotFallBackToReplica() throws Exception {
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        mvc.perform(get("/api/brokers/timer").param("instanceId", "instance-a").param("brokerName", "broker-a"))
                .andExpect(status().isConflict());
        verify(admin, never()).fetchBrokerRuntimeStats(anyString());
    }

    @Test
    void blankMasterAddressIsUnavailable() {
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().put(0L, " ");
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a")).hasMessageContaining("no registered master");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void blankBrokerIsRejectedBeforeResolvingInstance(String brokerName) {
        assertThatThrownBy(() -> service.inspect("instance-a", brokerName)).hasMessage("brokerName is required");
        verifyNoInteractions(resolver);
    }

    @Test
    void httpRequiresInstanceAndBrokerParameters() throws Exception {
        mvc.perform(get("/api/brokers/timer").param("brokerName", "broker-a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/brokers/timer").param("instanceId", "instance-a")).andExpect(status().isBadRequest());
        verifyNoInteractions(resolver);
    }
}
