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
import org.apache.rocketmq.remoting.protocol.body.HARuntimeInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.common.exception.GlobalExceptionHandler;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerHaTest {
    private final RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
    private final MQAdminExt admin = mock(MQAdminExt.class);
    private final BrokerHaService service = new BrokerHaService(resolver);
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(call -> call.<MqAdminExtFactory.AdminAction<?>>getArgument(1).apply(admin))
                .when(resolver).execute(anyString(), any());
        var cluster = new ClusterInfo();
        cluster.setBrokerAddrTable(new HashMap<>(Map.of("broker-a",
                new BrokerData("cluster-a", "broker-a", new HashMap<>(Map.of(
                        0L, "master:10911", 1L, "replica:10911"))))));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        mvc = MockMvcBuilders.standaloneSetup(new BrokerHaController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void httpSnapshotPreservesPhysicalOffsetsAndBothRoles() throws Exception {
        var master = new HARuntimeInfo();
        master.setMaster(true);
        master.setMasterCommitLogMaxOffset(9007199254740993L);
        master.setInSyncSlaveNums(1);
        var connection = new HARuntimeInfo.HAConnectionRuntimeInfo();
        connection.setAddr("replica:10912");
        connection.setInSync(true);
        connection.setSlaveAckOffset(9007199254740000L);
        connection.setDiff(993);
        connection.setTransferFromWhere(9007199254740001L);
        connection.setTransferredByteInSecond(1024);
        master.setHaConnectionInfo(List.of(connection));
        when(admin.getBrokerHAStatus("master:10911")).thenReturn(master);
        var slave = replica();
        when(admin.getBrokerHAStatus("replica:10911")).thenReturn(slave);

        mvc.perform(get("/api/brokers/ha").param("instanceId", "instance-a").param("brokerName", " broker-a "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.nodes[0].brokerId").value("0"))
                .andExpect(jsonPath("$.data.nodes[0].master").value(true))
                .andExpect(jsonPath("$.data.nodes[0].maxOffset").value("9007199254740993"))
                .andExpect(jsonPath("$.data.nodes[0].connections[0].differenceBytes").value("993"))
                .andExpect(jsonPath("$.data.nodes[0].connections[0].transferOffset").value("9007199254740001"))
                .andExpect(jsonPath("$.data.nodes[0].connections[0].bytesPerSecond").value("1024"))
                .andExpect(jsonPath("$.data.nodes[1].master").value(false))
                .andExpect(jsonPath("$.data.nodes[1].replica.masterAddress").value("master:10912"))
                .andExpect(jsonPath("$.data.nodes[1].replica.masterFlushOffset").value("200"))
                .andExpect(jsonPath("$.data.nodes[1].replica.lastReadTimestamp").value(1000));
        verify(resolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
    }

    @Test
    void failingMasterDoesNotHideReachableReplica() throws Exception {
        when(admin.getBrokerHAStatus("master:10911")).thenThrow(new IllegalStateException("unsupported HA service"));
        when(admin.getBrokerHAStatus("replica:10911")).thenReturn(replica());
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.complete()).isFalse();
        assertThat(result.nodes().getFirst().error()).isEqualTo("unsupported HA service");
        assertThat(result.nodes().getFirst().master()).isNull();
        assertThat(result.nodes().getLast().replica().maxOffset()).isEqualTo("300");
    }

    @Test
    void absentRuntimeDataIsUnavailableRatherThanHealthyZero() throws Exception {
        var incomplete = new HARuntimeInfo();
        incomplete.setHaClientRuntimeInfo(null);
        when(admin.getBrokerHAStatus("replica:10911")).thenReturn(incomplete);
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.complete()).isFalse();
        assertThat(result.nodes()).allSatisfy(node -> assertThat(node.error()).contains("no HA"));
    }

    @Test
    void emptyExceptionMessageStillProducesFailedNode() throws Exception {
        when(admin.getBrokerHAStatus(anyString())).thenThrow(new IllegalStateException());
        assertThat(service.inspect("instance-a", "broker-a").nodes())
                .allSatisfy(node -> assertThat(node.error()).isEqualTo("IllegalStateException"));
    }

    @Test
    void noConnectionsIsValidMasterObservation() throws Exception {
        var master = new HARuntimeInfo();
        master.setMaster(true);
        when(admin.getBrokerHAStatus(anyString())).thenReturn(master);
        var result = service.inspect("instance-a", "broker-a");
        assertThat(result.complete()).isTrue();
        assertThat(result.nodes()).allSatisfy(node -> {
            assertThat(node.connections()).isEmpty();
            assertThat(node.inSyncSlaveCount()).isZero();
        });
    }

    @Test
    void interruptionStopsRemainingProbesAndRestoresFlag() throws Exception {
        when(admin.getBrokerHAStatus("master:10911")).thenThrow(new InterruptedException("cancelled"));
        try {
            assertThatThrownBy(() -> service.inspect("instance-a", "broker-a"))
                    .isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(admin, never()).getBrokerHAStatus("replica:10911");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void brokerOutsideSelectedInstanceCannotSupplyAnAddress() throws Exception {
        mvc.perform(get("/api/brokers/ha").param("instanceId", "instance-a").param("brokerName", "other:10911"))
                .andExpect(jsonPath("$.code").value(404));
        verify(admin, never()).getBrokerHAStatus(anyString());
    }

    @Test
    void missingParametersAndBlankBrokerAreRejected() throws Exception {
        mvc.perform(get("/api/brokers/ha").param("instanceId", "instance-a"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/brokers/ha").param("brokerName", "broker-a"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/brokers/ha").param("instanceId", "instance-a").param("brokerName", " "))
                .andExpect(jsonPath("$.code").value(400));
        assertThatThrownBy(() -> service.inspect("instance-a", null)).hasMessageContaining("brokerName");
    }

    @Test
    void brokerWithNoRegisteredReplicasIsNotACompleteSnapshot() throws Exception {
        var cluster = admin.examineBrokerClusterInfo();
        cluster.getBrokerAddrTable().get("broker-a").setBrokerAddrs(new HashMap<>());
        assertThatThrownBy(() -> service.inspect("instance-a", "broker-a"))
                .hasMessageContaining("no registered replicas");
    }

    private HARuntimeInfo replica() {
        var info = new HARuntimeInfo();
        var client = info.getHaClientRuntimeInfo();
        client.setMasterAddr("master:10912");
        client.setMaxOffset(300);
        client.setMasterFlushOffset(200);
        client.setTransferredByteInSecond(2048);
        client.setLastReadTimestamp(1000);
        client.setLastWriteTimestamp(2000);
        return info;
    }
}
