/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
*/
package org.apache.rocketmq.studio.ops.alert;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.metrics.AlertingProperties;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationEmailRecipientsTest {
    private final RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
    private final SettingsRepository settings = mock(SettingsRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private NotificationOutboxService service;

    @BeforeEach
    void setUpTest() {
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("email");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString(), anyInt())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        service = new NotificationOutboxService(mapper, settings, mock(AlertSilenceService.class), alerts,
                mock(OperationAuditService.class), new RestTemplate(), () -> mailSender,
                new AlertingProperties(), mock(ScheduledExecutorService.class));
    }

    @AfterEach
    void closeTest() {
        service.closeHeartbeatExecutor();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ops@example.com, backup@example.com",
        "\"Ops, East\" <ops@example.com>; backup@example.com",
        "\"Ops; East\" <ops@example.com>, backup@example.com",
        "ops@example.com (Ops, on call), backup@example.com",
        "Ops (East; on call) <ops@example.com>; backup@example.com",
        "ops@example.com (Ops (East, primary)), backup@example.com",
        "ops@example.com (Ops \\(East, primary), backup@example.com",
        "ops@example.com (Ops \\), primary), backup@example.com",
        "ops@example.com (Ops \"East, primary), backup@example.com",
        "\"Ops (East), Team\" <ops@example.com>, backup@example.com",
        "\"Ops \\\"East, Team\" <ops@example.com>; backup@example.com",
        "\"Ops, Team\" (East; primary) <ops@example.com>; backup@example.com"
    })
    void dispatchesCompleteAddressesWithoutSplittingCommentsOrQuotesTest(String recipients) {
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .emailRecipients(recipients).build());

        service.dispatch();

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("ops@example.com", "backup@example.com");
        assertStatus("DELIVERED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ops@example.com (unfinished, backup@example.com",
        "ops@example.com (Ops (East, primary), backup@example.com",
        "\"unfinished <ops@example.com>, backup@example.com",
        "ops@example.com, invalid@@example.com"
    })
    void retriesInvalidAddressesWithoutSendingAnyEmailTest(String recipients) {
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .emailRecipients(recipients).build());

        service.dispatch();

        verifyNoInteractions(mailSender);
        assertStatus("RETRY_WAIT");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertStatus(String expected) {
        ArgumentCaptor<UpdateWrapper> update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(any(), update.capture());
        update.getValue().getSqlSet();
        assertThat(update.getValue().getParamNameValuePairs().values()).contains(expected);
    }
}
