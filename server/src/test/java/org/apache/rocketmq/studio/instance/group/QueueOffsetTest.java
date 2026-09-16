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
package org.apache.rocketmq.studio.instance.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class QueueOffsetTest {
    private static final MessageQueue QUEUE = new MessageQueue("orders", "broker-a", 1);
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final MqAdminExtFactory factory = new MqAdminExtFactory() {
        @Override
        protected DefaultMQAdminExt newAdmin(RPCHook hook) {
            return admin;
        }
    };
    private final TopicStatsTable topic = new TopicStatsTable();
    private final ConsumeStats consumption = new ConsumeStats();
    private final TopicOffset range = new TopicOffset();
    private final OffsetWrapper current = new OffsetWrapper();
    private QueueOffsetService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        when(instances.findByIdentifier("instance-a")).thenReturn(Optional.of(InstanceVO.builder()
                .name("instance-a").vendor(InstanceVendor.APACHE).endpoint("selected:9876").build()));
        var resolver = new RuntimeAdminClientResolver(instances, factory, new MqAdminProperties(), new MqClientPool());
        service = new QueueOffsetService(resolver, new OperationAuditService(audits));
        var route = new TopicRouteData();
        route.setBrokerDatas(List.of(new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "master:10911", 1L, "replica:10911")))));
        when(admin.examineTopicRouteInfo("orders")).thenReturn(route);
        when(admin.examineConsumerConnectionInfo("cg-orders", "master:10911"))
                .thenReturn(new ConsumerConnection());
        range.setMinOffset(10);
        range.setMaxOffset(100);
        topic.getOffsetTable().put(QUEUE, range);
        when(admin.examineTopicStats("orders")).thenReturn(topic);
        current.setConsumerOffset(40);
        consumption.getOffsetTable().put(QUEUE, current);
        when(admin.examineConsumeStats("master:10911", "cg-orders", "orders", 5000)).thenReturn(consumption);
        mvc = MockMvcBuilders.standaloneSetup(new QueueOffsetController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        Thread.interrupted();
        factory.shutdown();
    }

    @Test
    void httpPreviewShowsReplayAndDoesNotWriteOffsets() throws Exception {
        mvc.perform(post("/api/groups/queue-offset/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(request("20", null))))
                .andExpect(jsonPath("$.data.currentOffset").value("40"))
                .andExpect(jsonPath("$.data.targetOffset").value("20"))
                .andExpect(jsonPath("$.data.offsetDelta").value("-20"))
                .andExpect(jsonPath("$.data.projectedLag").value("80"));
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        verifyNoInteractions(audits);
    }

    @Test
    void httpApplyOnlyUpdatesChosenQueueWithoutTimestampResetFallback() throws Exception {
        mvc.perform(post("/api/groups/queue-offset").contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(request("60", "40"))))
                .andExpect(jsonPath("$.data.offsetDelta").value("20"));
        verify(admin).setNamesrvAddr("selected:9876");
        verify(admin).updateConsumeOffset("master:10911", "cg-orders", QUEUE, 60);
        verify(admin, never()).resetOffsetByQueueId(anyString(), anyString(), anyString(), anyInt(), anyLong());
        verify(admin, never()).resetOffsetNew(anyString(), anyString(), anyLong());
        verify(audits).insert(any(RmqOperationAudit.class));
    }

    @Test
    void applyRejectsPositionChangesAfterPreview() throws Exception {
        service.preview(request("60", null));
        current.setConsumerOffset(41);
        assertThatThrownBy(() -> service.apply(request("60", "40"))).hasMessageContaining("changed");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void applyRechecksConnectionsAndRejectsRestartedConsumers() throws Exception {
        service.preview(request("60", null));
        var online = new ConsumerConnection();
        online.getConnectionSet().add(new Connection());
        when(admin.examineConsumerConnectionInfo("cg-orders", "master:10911")).thenReturn(online);
        assertThatThrownBy(() -> service.apply(request("60", "40"))).hasMessageContaining("Stop all consumers");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void brokerOfflineResponseAndEmptyConnectionSetAreRecognized() throws Exception {
        when(admin.examineConsumerConnectionInfo("cg-orders", "master:10911"))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"))
                .thenThrow(new MQBrokerException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"))
                .thenReturn(new ConsumerConnection());
        assertThat(service.preview(request("60", null)).targetOffset()).isEqualTo("60");
        assertThat(service.preview(request("60", null)).targetOffset()).isEqualTo("60");
        assertThat(service.preview(request("60", null)).targetOffset()).isEqualTo("60");
    }

    @Test
    void permissionFailureAndNullConnectionResponseDoNotMeanOffline() throws Exception {
        when(admin.examineConsumerConnectionInfo("cg-orders", "master:10911"))
                .thenThrow(new MQBrokerException(ResponseCode.NO_PERMISSION, "denied"))
                .thenThrow(new MQClientException(ResponseCode.SYSTEM_ERROR, "unavailable"))
                .thenReturn(null);
        assertThatThrownBy(() -> service.preview(request("60", null))).hasMessageContaining("denied");
        assertThatThrownBy(() -> service.preview(request("60", null))).hasMessageContaining("unavailable");
        assertThatThrownBy(() -> service.preview(request("60", null))).hasMessageContaining("Stop all consumers");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9", "101"})
    void offsetsOutsideCurrentRetentionRangeCannotBeApplied(String offset) {
        assertThatThrownBy(() -> service.apply(request(offset, "40"))).hasMessageContaining("readable queue range");
    }

    @Test
    void endOffsetAndLongPrecisionArePreserved() {
        range.setMaxOffset(9007199254740993L);
        current.setConsumerOffset(9007199254740990L);
        var result = service.preview(request("9007199254740993", null));
        assertThat(result.targetOffset()).isEqualTo("9007199254740993");
        assertThat(result.currentOffset()).isEqualTo("9007199254740990");
        assertThat(result.offsetDelta()).isEqualTo("3");
        assertThat(result.projectedLag()).isEqualTo("0");
    }

    @Test
    void retentionChangeAfterPreviewIsRechecked() {
        service.preview(request("20", null));
        range.setMinOffset(21);
        assertThatThrownBy(() -> service.apply(request("20", "40"))).hasMessageContaining("readable queue range");
    }

    @Test
    void missingQueueOrCommitIsNotAnImplicitZero() {
        topic.getOffsetTable().clear();
        assertThatThrownBy(() -> service.preview(request("20", null))).hasMessageContaining("not present");
        topic.getOffsetTable().put(QUEUE, range);
        consumption.getOffsetTable().clear();
        assertThatThrownBy(() -> service.preview(request("20", null))).hasMessageContaining("no committed");
        current.setConsumerOffset(-1);
        consumption.getOffsetTable().put(QUEUE, current);
        assertThatThrownBy(() -> service.preview(request("20", null))).hasMessageContaining("no committed");
    }

    @Test
    void missingMasterCannotFallBackToReplica() throws Exception {
        admin.examineTopicRouteInfo("orders").getBrokerDatas().getFirst().getBrokerAddrs().remove(0L);
        assertThatThrownBy(() -> service.apply(request("20", "40"))).hasMessageContaining("no registered master");
        verify(admin, never()).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void invalidOffsetAndMissingExpectedPositionAreRejectedBeforeSdkWork() throws Exception {
        for (String invalid : new String[] {"-1", "9223372036854775808", "bad"}) {
            assertThatThrownBy(() -> service.preview(request(invalid, null))).hasMessageContaining("64-bit offset");
        }
        assertThatThrownBy(() -> service.apply(request("20", null))).hasMessageContaining("64-bit offset");
        verify(admin, never()).examineTopicRouteInfo(anyString());
    }

    @Test
    void failedWriteIsAuditedAndKeepsItsCause() throws Exception {
        doThrow(new MQBrokerException(ResponseCode.SYSTEM_ERROR, "write rejected")).when(admin)
                .updateConsumeOffset("master:10911", "cg-orders", QUEUE, 20);
        assertThatThrownBy(() -> service.apply(request("20", "40"))).hasMessageContaining("write rejected");
        var capture = org.mockito.ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(audits).insert(capture.capture());
        assertThat(capture.getValue().getResult()).isEqualTo("FAILED");
        assertThat(capture.getValue().getDetail()).contains("previous=40", "target=20", "queueId=1");
    }

    @Test
    void interruptedWriteRestoresInterruptFlag() throws Exception {
        doThrow(new InterruptedException("cancelled")).when(admin)
                .updateConsumeOffset("master:10911", "cg-orders", QUEUE, 20);
        assertThatThrownBy(() -> service.apply(request("20", "40"))).hasMessageContaining("cancelled");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void auditFailureDoesNotRetryAnAcceptedUpdate() throws Exception {
        when(audits.insert(any(RmqOperationAudit.class))).thenThrow(new IllegalStateException("audit unavailable"));
        assertThat(service.apply(request("20", "40")).targetOffset()).isEqualTo("20");
        verify(admin).updateConsumeOffset("master:10911", "cg-orders", QUEUE, 20);
    }

    @Test
    void httpContractRejectsMissingQueueAndNegativeQueueId() throws Exception {
        for (String body : List.of("{}", "{\"instanceId\":\"instance-a\",\"group\":\"cg-orders\","
                + "\"topic\":\"orders\",\"brokerName\":\"broker-a\",\"queueId\":-1,\"offset\":\"20\"}")) {
            mvc.perform(post("/api/groups/queue-offset/preview").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    private QueueOffsetCommand request(String offset, String expected) {
        return new QueueOffsetCommand("instance-a", "cg-orders", "orders", "broker-a", 1, offset, expected);
    }
}
