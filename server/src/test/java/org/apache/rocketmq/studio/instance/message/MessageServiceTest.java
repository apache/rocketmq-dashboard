/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Test
    void rejectsNegativeQueueCoordinatesBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        MessageService service = new MessageService(provider, mock(InstanceProviderRegistry.class),
                mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.pullMessageAtOffset("instance-a", "TopicA", "broker-a", -1, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("queueId must not be negative");
        assertThatThrownBy(() -> service.pullMessageAtOffset("instance-a", "TopicA", "broker-a", 0, -1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("offset must not be negative");

        verifyNoInteractions(provider);
    }

    @Test
    void rejectsKeyQueryWithoutTopicBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        MessageService service = new MessageService(provider, registry, mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.queryMessages(null, null, null, null, "order-1", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic is required when key is specified");

        verifyNoInteractions(provider);
    }

    @Test
    void rejectsMessageIdQueryWithoutTopicBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        MessageService service = new MessageService(provider, registry, mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.queryMessages(null, null, "msg-001", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic is required when msgId is specified");

        verifyNoInteractions(provider, registry);
    }

    @Test
    void rejectsBlankMessageTraceIdBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        MessageService service = new MessageService(provider, registry, mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.getMessageTrace("instance-a", "  ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("msgId is required");

        verifyNoInteractions(provider, registry);
    }

    @Test
    void rejectsReversedTopicQueryWindowBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        MessageService service = new MessageService(provider, registry, mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.queryMessages("instance-a", "TopicA", null, null, null, 200L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime must be before endTime");

        verifyNoInteractions(provider);
    }

    @Test
    void rejectsTopicQueryWindowLongerThanSevenDaysBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        MessageService service = new MessageService(provider, registry, mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.queryMessages("instance-a", "TopicA", null, null, null, 0L,
                8L * 24 * 60 * 60 * 1000))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic query time range must not exceed 7 days");

        verifyNoInteractions(provider);
    }

    @Test
    void recordsProviderNeutralMessageQueryHistory() {
        MessageProvider fallback = mock(MessageProvider.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        QueryHistoryService history = mock(QueryHistoryService.class);
        MessageService service = new MessageService(fallback, registry, history, mock(OperationAuditService.class));
        when(registry.byInstanceId("cloud-instance")).thenReturn(Optional.of(provider));
        when(provider.queryMessages("cloud-instance", "orders", null, null, "ORDER-1", null, null))
                .thenReturn(List.of(MessageRecordVO.builder().msgId("msg-1").build()));

        service.queryMessages("cloud-instance", "orders", null, null, "ORDER-1", null, null);

        verify(history).recordMessageQuery("cloud-instance", "KEY", "orders", null, null,
                "ORDER-1", null, null, 1);
        verifyNoInteractions(fallback);
    }

    @Test
    void directsMessageConsumptionThroughSelectedProviderAndAuditsItTest() {
        MessageProvider fallback = mock(MessageProvider.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        DirectConsumeMessageDTO request = new DirectConsumeMessageDTO();
        request.setInstanceId("instance-a");
        request.setTopic("orders");
        request.setMsgId("msg-1");
        request.setConsumerGroup("billing");
        request.setClientId("client-a");
        DirectConsumeMessageResultVO expected = DirectConsumeMessageResultVO.builder()
                .consumeResult("CR_SUCCESS").build();
        when(registry.byInstanceId("instance-a")).thenReturn(Optional.of(provider));
        when(provider.consumeMessageDirectly(request)).thenReturn(expected);
        MessageService service = new MessageService(fallback, registry, mock(QueryHistoryService.class), audit);

        org.assertj.core.api.Assertions.assertThat(service.consumeMessageDirectly(request)).isSameAs(expected);

        verify(audit).record(org.mockito.ArgumentMatchers.eq("DIRECT_CONSUME_MESSAGE"),
                org.mockito.ArgumentMatchers.eq("MESSAGE"), org.mockito.ArgumentMatchers.eq("msg-1"),
                org.mockito.ArgumentMatchers.eq("instance-a"), org.mockito.ArgumentMatchers.contains("billing"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void rejectsOverflowingTopicQueryWindowBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        MessageService service = new MessageService(provider, registry, mock(QueryHistoryService.class), mock(OperationAuditService.class));

        assertThatThrownBy(() -> service.queryMessages("instance-a", "TopicA", null, null, null,
                0L, Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic query time range must not exceed 7 days");

        verifyNoInteractions(provider, registry);
    }

    @Test
    void pageQuerySlicesProviderResultAndSignalsBoundedTopicResult() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        QueryHistoryService history = mock(QueryHistoryService.class);
        MessageService service = new MessageService(provider, registry, history, mock(OperationAuditService.class));
        when(registry.byInstanceId("instance-a")).thenReturn(Optional.empty());
        when(provider.queryMessages("instance-a", "TopicA", null, null, null, 1000L, 2000L))
                .thenReturn(java.util.stream.IntStream.range(0, 200)
                        .mapToObj(index -> MessageRecordVO.builder().msgId("msg-" + index).build()).toList());

        MessageQueryPageVO page = service.queryMessagesPage("instance-a", "TopicA", null, null, null,
                1000L, 2000L, 2, 50);

        assertThat(page.getItems()).hasSize(50);
        assertThat(page.getTotal()).isEqualTo(200);
        assertThat(page.isResultMayBeTruncated()).isTrue();
    }

    @Test
    void pageQueryRecordsHistoryOnlyForTheFirstPage() {
        MessageProvider provider = mock(MessageProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        QueryHistoryService history = mock(QueryHistoryService.class);
        MessageService service = new MessageService(provider, registry, history, mock(OperationAuditService.class));
        when(registry.byInstanceId("instance-a")).thenReturn(Optional.empty());
        when(provider.queryMessages("instance-a", "TopicA", null, null, null, 1000L, 2000L))
                .thenReturn(List.of(MessageRecordVO.builder().msgId("msg-1").build()));

        service.queryMessagesPage("instance-a", "TopicA", null, null, null,
                1000L, 2000L, 1, 50);
        service.queryMessagesPage("instance-a", "TopicA", null, null, null,
                1000L, 2000L, 2, 50);

        verify(provider, org.mockito.Mockito.times(2))
                .queryMessages("instance-a", "TopicA", null, null, null, 1000L, 2000L);
        verify(history, org.mockito.Mockito.times(1)).recordMessageQuery(
                "instance-a", "TOPIC", "TopicA", null, null, null, 1000L, 2000L, 1);
    }
}
