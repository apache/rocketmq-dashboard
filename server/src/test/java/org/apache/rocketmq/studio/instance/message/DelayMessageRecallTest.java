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
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.producer.RecallMessageHandle;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.exception.GlobalExceptionHandler;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class DelayMessageRecallTest {
    private static final String HANDLE = RecallMessageHandle.HandleV1.buildHandle(
            "orders", "broker-a", "1888825600000", "original-message");
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final RmqOperationAuditMapper audits = mock(RmqOperationAuditMapper.class);
    private final MqClientPool pool = new MqClientPool();
    private final ObjectMapper json = new ObjectMapper();
    private MockedConstruction<DefaultMQProducer> producers;
    private DelayMessageRecallService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        producers = mockConstruction(DefaultMQProducer.class);
        when(instances.findByIdentifier("instance-a")).thenReturn(Optional.of(InstanceVO.builder()
                .name("instance-a").vendor(InstanceVendor.APACHE).endpoint("selected:9876").build()));
        var resolver = new RuntimeAdminClientResolver(instances, mock(MqAdminExtFactory.class),
                new MqAdminProperties(), pool);
        service = new DelayMessageRecallService(resolver, new OperationAuditService(audits));
        mvc = MockMvcBuilders.standaloneSetup(new DelayMessageRecallController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        pool.shutdown();
        producers.close();
    }

    @Test
    void previewDecodesOriginalReceiptWithoutOpeningProducerOrAuditingMutation() throws Exception {
        mvc.perform(post("/api/messages/recall/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request("orders", HANDLE))))
                .andExpect(jsonPath("$.data.topic").value("orders"))
                .andExpect(jsonPath("$.data.brokerName").value("broker-a"))
                .andExpect(jsonPath("$.data.messageId").value("original-message"))
                .andExpect(jsonPath("$.data.deliveryTimestamp").value(1888825600000L));
        assertThat(producers.constructed()).isEmpty();
        verifyNoInteractions(audits);
    }

    @Test
    void recallUsesSelectedEndpointAndAuditsAcceptedMessageWithoutHandle() throws Exception {
        var producer = producer();
        when(producer.recallMessage("orders", HANDLE)).thenReturn("original-message");
        mvc.perform(post("/api/messages/recall").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request(" orders ", " " + HANDLE + " "))))
                .andExpect(jsonPath("$.data.messageId").value("original-message"))
                .andExpect(jsonPath("$.data.acceptedAt").isNumber());
        verify(producer).setNamesrvAddr("selected:9876");
        verify(producer).recallMessage("orders", HANDLE);
        var record = audit();
        assertThat(record.getOperation()).isEqualTo("RECALL_DELAY_MESSAGE");
        assertThat(record.getResult()).isEqualTo("SUCCESS");
        assertThat(record.getResourceName()).isEqualTo("original-message");
        assertThat(record.getClusterId()).isEqualTo("instance-a");
        assertThat(record.getDetail()).contains("topic=orders", "broker=broker-a").doesNotContain(HANDLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-handle", "%%%%", "djIgYSBiIDEgYw==", "IA=="})
    void invalidHandleFailsBeforeProducerCreation(String handle) {
        assertThatThrownBy(() -> service.recall(request("orders", handle)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("invalid");
        assertThat(producers.constructed()).isEmpty();
        verifyNoInteractions(audits);
    }

    @Test
    void topicMismatchCannotRedirectTheRecall() {
        assertThatThrownBy(() -> service.recall(request("other-topic", HANDLE)))
                .hasMessageContaining("does not match");
        assertThat(producers.constructed()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "-1", "0", "9223372036854775808"})
    void malformedTimestampIsRejectedWithoutApplyingALocalClockDeadline(String timestamp) {
        String handle = RecallMessageHandle.HandleV1.buildHandle("orders", "broker-a", timestamp, "original-message");
        assertThatThrownBy(() -> service.preview(request("orders", handle))).isInstanceOf(BusinessException.class);
    }

    @Test
    void malformedTargetFieldsAreRejected() {
        String handle = RecallMessageHandle.HandleV1.buildHandle("orders", "", "1888825600000", "original-message");
        assertThatThrownBy(() -> service.recall(request("orders", handle))).hasMessageContaining("invalid target");
    }

    @Test
    void brokerRejectionKeepsFailureAndRecordsItOnce() throws Exception {
        when(producer().recallMessage("orders", HANDLE))
                .thenThrow(new MQBrokerException(ResponseCode.ILLEGAL_OPERATION, "timestamp invalid"));
        mvc.perform(post("/api/messages/recall").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request("orders", HANDLE))))
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("timestamp invalid")));
        assertThat(audit().getResult()).isEqualTo("FAILED");
    }

    @Test
    void missingReceiptIsNotReportedAsAccepted() throws Exception {
        producer();
        assertThatThrownBy(() -> service.recall(request("orders", HANDLE))).hasMessageContaining("no recall receipt");
        assertThat(audit().getResult()).isEqualTo("FAILED");
    }

    @Test
    void auditStorageFailureDoesNotConvertAcceptanceIntoRetryableFailure() throws Exception {
        when(producer().recallMessage("orders", HANDLE)).thenReturn("original-message");
        when(audits.insert(any(RmqOperationAudit.class))).thenThrow(new IllegalStateException("storage unavailable"));
        assertThat(service.recall(request("orders", HANDLE)).messageId()).isEqualTo("original-message");
        verify(producer()).recallMessage("orders", HANDLE);
    }

    @Test
    void interruptionIsPreservedAndAudited() throws Exception {
        var producer = producer();
        doThrow(new InterruptedException("cancelled")).when(producer).recallMessage(anyString(), anyString());
        assertThatThrownBy(() -> service.recall(request("orders", HANDLE))).hasMessageContaining("cancelled");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(audit().getResult()).isEqualTo("FAILED");
    }

    @Test
    void cloudInstanceCannotFallBackToDefaultApacheEndpoint() {
        when(instances.findByIdentifier("instance-a")).thenReturn(Optional.of(InstanceVO.builder()
                .name("instance-a").vendor(InstanceVendor.ALIYUN).endpoint("cloud").build()));
        assertThatThrownBy(() -> service.preview(request("orders", HANDLE))).hasMessageContaining("only supports Apache");
        assertThatThrownBy(() -> service.recall(request("orders", HANDLE))).hasMessageContaining("only supports Apache");
        assertThat(producers.constructed()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"instanceId\":\"instance-a\",\"topic\":\"orders\",\"recallHandle\":\" \"}"})
    void requiredHttpFieldsAreValidated(String body) throws Exception {
        mvc.perform(post("/api/messages/recall").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(400));
        assertThat(producers.constructed()).isEmpty();
    }

    private DefaultMQProducer producer() {
        pool.withProducer("selected:9876", null, null, client -> client);
        return producers.constructed().getFirst();
    }

    private RecallMessageDTO request(String topic, String handle) {
        return new RecallMessageDTO("instance-a", topic, handle);
    }

    private RmqOperationAudit audit() {
        var capture = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(audits).insert(capture.capture());
        return capture.getValue();
    }
}
