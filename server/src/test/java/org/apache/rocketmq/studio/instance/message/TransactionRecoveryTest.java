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
package org.apache.rocketmq.studio.instance.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionRecoveryTest {
    private static final String ID = "7F00000100002A9F000000000000002A";
    private static final String SOURCE = TopicValidator.RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC;
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
    private final MessageExt message = new MessageExt();
    private TransactionRecoveryService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        when(instances.findByIdentifier("instance-a")).thenReturn(Optional.of(InstanceVO.builder()
                .name("instance-a").vendor(InstanceVendor.APACHE).endpoint("selected:9876").build()));
        service = new TransactionRecoveryService(new RuntimeAdminClientResolver(instances, factory,
                new MqAdminProperties(), new MqClientPool()), new OperationAuditService(audits));
        cluster.setBrokerAddrTable(new HashMap<>(Map.of("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "127.0.0.1:10911", 1L, "127.0.0.2:10911"))))));
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        message.setTopic(SOURCE);
        message.setStoreHost(new InetSocketAddress("127.0.0.1", 10911));
        message.setCommitLogOffset(42);
        message.setStoreTimestamp(1788825600000L);
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_REAL_TOPIC, "orders");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_PRODUCER_GROUP, "payment-producer");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_TRANSACTION_CHECK_TIMES, "15");
        message.setTransactionId("transaction-a");
        message.setBody("private transaction body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(admin.viewMessage(SOURCE, ID)).thenReturn(message);
        when(admin.resumeCheckHalfMessage(SOURCE, ID)).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new TransactionRecoveryController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        factory.shutdown();
        Thread.interrupted();
    }

    @Test
    void previewUsesNativeDiscardTopicAndReturnsMetadataWithoutMutating() throws Exception {
        mvc.perform(post("/api/messages/transaction-recovery/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TransactionRecoveryCommand("instance-a", ID.toLowerCase()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offsetMessageId").value(ID))
                .andExpect(jsonPath("$.data.originalTopic").value("orders"))
                .andExpect(jsonPath("$.data.producerGroup").value("payment-producer"))
                .andExpect(jsonPath("$.data.checkTimes").value("15"))
                .andExpect(jsonPath("$.data.transactionId").value("transaction-a"))
                .andExpect(jsonPath("$.data.body").doesNotExist());
        verify(admin).setNamesrvAddr("selected:9876");
        verify(admin).viewMessage("TRANS_CHECK_MAX_TIME_TOPIC", ID);
        verify(admin, never()).resumeCheckHalfMessage(anyString(), anyString());
        verifyNoInteractions(audits);
    }

    @Test
    void applyRevalidatesSourceThenRecordsTheAcceptedRecovery() throws Exception {
        mvc.perform(post("/api/messages/transaction-recovery").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(command(ID))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.originalTopic").value("orders"));
        var order = org.mockito.Mockito.inOrder(admin);
        order.verify(admin).viewMessage(SOURCE, ID);
        order.verify(admin).resumeCheckHalfMessage(SOURCE, ID);
        var audit = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getResult()).isEqualTo("SUCCESS");
        assertThat(audit.getValue().getDetail()).contains("orders", "payment-producer", "127.0.0.1:10911");
        assertThat(audit.getValue().getDetail()).doesNotContain("private transaction body");
    }

    @Test
    void ordinaryMessagesCannotBeReinsertedIntoTheHalfQueue() throws Exception {
        message.setTopic("orders");
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("Only messages in TRANS_CHECK_MAX_TIME_TOPIC");
        verify(admin, never()).resumeCheckHalfMessage(anyString(), anyString());
    }

    @Test
    void physicalMessageFromAnotherInstanceNeverReachesViewMessage() throws Exception {
        assertThatThrownBy(() -> service.preview(command("7F00000300002A9F000000000000002A")))
                .hasMessageContaining("outside the selected instance");
        verify(admin, never()).viewMessage(anyString(), anyString());
    }

    @Test
    void topologyUnavailableFailsBeforeReadingTheEmbeddedAddress() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("NameServer unavailable"));
        assertThatThrownBy(() -> service.preview(command(ID))).hasMessageContaining("NameServer unavailable");
        verify(admin, never()).viewMessage(anyString(), anyString());
    }

    @Test
    void formerMasterCannotReceiveARecoveryWrite() throws Exception {
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().put(0L, "127.0.0.2:10911");
        cluster.getBrokerAddrTable().get("broker-a").getBrokerAddrs().put(1L, "127.0.0.1:10911");
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("registered master");
        verify(admin, never()).viewMessage(anyString(), anyString());
    }

    @Test
    void missingMessageReturnsNotFound() throws Exception {
        when(admin.viewMessage(SOURCE, ID)).thenReturn(null);
        assertThatThrownBy(() -> service.preview(command(ID))).hasMessageContaining("was not found");
    }

    @Test
    void brokerCannotRedirectRecoveryToADifferentPhysicalLocation() throws Exception {
        message.setStoreHost(new InetSocketAddress("127.0.0.3", 10911));
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("physical location");
        message.setStoreHost(new InetSocketAddress("127.0.0.1", 10911));
        message.setCommitLogOffset(43);
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("physical location");
        verify(admin, never()).resumeCheckHalfMessage(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REAL_TOPIC", "PGROUP"})
    void missingOriginalDestinationOrProducerGroupPreventsRecovery(String property) throws Exception {
        message.getProperties().remove(property);
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("missing its original");
        MessageAccessor.putProperty(message, property, " ");
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("missing its original");
        verify(admin, never()).resumeCheckHalfMessage(anyString(), anyString());
    }

    @Test
    void rejectedRecoveryIsNotReturnedAsAccepted() throws Exception {
        when(admin.resumeCheckHalfMessage(SOURCE, ID)).thenReturn(false);
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("did not accept");
        var audit = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getResult()).isEqualTo("FAILED");
    }

    @Test
    void interruptedRecoveryPreservesFlagAndFailure() throws Exception {
        when(admin.resumeCheckHalfMessage(SOURCE, ID)).thenThrow(new InterruptedException("cancelled"));
        assertThatThrownBy(() -> service.recover(command(ID))).hasMessageContaining("cancelled");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void auditFailureDoesNotTurnAcknowledgedRecoveryIntoFailure() {
        doThrow(new IllegalStateException("audit unavailable")).when(audits).insert(any(RmqOperationAudit.class));
        assertThat(service.recover(command(ID)).offsetMessageId()).isEqualTo(ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"client-unique-id", " 7F00000100002A9F000000000000002A", "GG00000100002A9F000000000000002A"})
    void malformedPhysicalIdsFailBeforeResolvingClients(String id) {
        assertThatThrownBy(() -> service.preview(command(id))).hasMessageContaining("physical offset message ID");
        verifyNoInteractions(admin);
    }

    @Test
    void negativeOffsetCannotReachBroker() throws Exception {
        assertThatThrownBy(() -> service.preview(command("7F00000100002A9FFFFFFFFFFFFFFFFF")))
                .hasMessageContaining("non-negative");
        verify(admin, never()).viewMessage(anyString(), anyString());
    }

    @Test
    void httpRejectsMissingInstanceOrMalformedId() throws Exception {
        mvc.perform(post("/api/messages/transaction-recovery/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offsetMessageId\":\"bad\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(admin);
    }

    private TransactionRecoveryCommand command(String id) {
        return new TransactionRecoveryCommand("instance-a", id);
    }
}
