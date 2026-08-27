/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
*/
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.TimeZone;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationOutboxServiceTest {
    @Test
    void schedulesOutboxWorkInUtcRegardlessOfTheJvmDefaultTimeZoneTest() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
            RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
            row.setId(8L);
            when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                    .thenReturn(List.of(row));
            when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                    any(LocalDateTime.class), anyString())).thenReturn(0);

            new NotificationOutboxService(mapper, mock(SettingsRepository.class), mock(AlertSilenceService.class),
                    mock(AlertRepository.class), mock(OperationAuditService.class)).dispatch();

            org.mockito.ArgumentCaptor<LocalDateTime> now = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
            verify(mapper).findDispatchable(now.capture(), any(LocalDateTime.class), any(Integer.class));
            assertThat(Duration.between(now.getValue().toInstant(ZoneOffset.UTC), java.time.Instant.now()).abs())
                    .isLessThan(Duration.ofSeconds(2));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void enqueuesEachSupportedChannelOnceUnlessTheEventIsSilencedTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        AlertSilenceService silences = mock(AlertSilenceService.class);
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).domain(AlertDomain.BUSINESS)
                .channels(List.of("dingtalk", "sms", "dingtalk", "email")).build();
        SystemAlertVO alert = SystemAlertVO.builder().id(9L).level(AlertLevel.warning).title("Lag")
                .description("lag is high").instanceId("local").time(LocalDateTime.now()).build();
        NotificationOutboxService service = new NotificationOutboxService(mapper, mock(SettingsRepository.class),
                silences, mock(AlertRepository.class), mock(OperationAuditService.class));

        when(silences.activeUntil(rule, "local", java.util.Map.of(), alert.getTime())).thenReturn(null);
        service.enqueue(alert, rule);
        verify(mapper, org.mockito.Mockito.times(3)).insert(any(RmqAlertNotificationOutbox.class));

        when(silences.activeUntil(rule, "local", java.util.Map.of(), alert.getTime()))
                .thenReturn(alert.getTime().plusHours(1));
        service.enqueue(alert, rule);
        verify(mapper, org.mockito.Mockito.times(6)).insert(any(RmqAlertNotificationOutbox.class));
    }

    @Test
    void snapshotsTheRenderedRuleTemplateWhenEnqueuingTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).domain(AlertDomain.CLUSTER).name("Disk warning")
                .metric("broker.disk.usage_ratio").threshold(85).channels(List.of("dingtalk"))
                .notificationTemplate("${ruleName}: ${value}${thresholdUnit} on ${instanceId}").build();
        SystemAlertVO alert = SystemAlertVO.builder().id(9L).title("Disk warning").instanceId("local")
                .currentValue(86.0).labels(java.util.Map.of()).time(LocalDateTime.now()).build();
        AlertSilenceService silences = mock(AlertSilenceService.class);
        when(silences.activeUntil(rule, "local", java.util.Map.of(), alert.getTime())).thenReturn(null);

        new NotificationOutboxService(mapper, mock(SettingsRepository.class), silences,
                mock(AlertRepository.class), mock(OperationAuditService.class)).enqueue(alert, rule);

        org.mockito.ArgumentCaptor<RmqAlertNotificationOutbox> row =
                org.mockito.ArgumentCaptor.forClass(RmqAlertNotificationOutbox.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getMessageContent()).isEqualTo("Disk warning: 86.0 on local");
    }

    @Test
    void defersDeliveryUntilTheActiveSilenceEndsTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        AlertSilenceService silences = mock(AlertSilenceService.class);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        LocalDateTime silenceEndsAt = LocalDateTime.now().plusHours(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L).ruleId(4L)
                .domain(AlertDomain.BUSINESS).instanceId("local").build()));
        when(silences.activeUntil(any(AlertRuleVO.class), org.mockito.ArgumentMatchers.eq("local"),
                org.mockito.ArgumentMatchers.eq(java.util.Map.of()), any(LocalDateTime.class))).thenReturn(silenceEndsAt);

        new NotificationOutboxService(mapper, mock(SettingsRepository.class), silences, alerts, audit).dispatch();

        verify(mapper).update(any(), any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void dispatchesDingTalkDeliveryAndMarksTheOutboxRowDeliveredTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        SettingsRepository settings = mock(SettingsRepository.class);
        AlertSilenceService silences = mock(AlertSilenceService.class);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local")
                .labels(java.util.Map.of("topic", "orders")).build()));
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook("https://example.com/hook").build());
        server.expect(once(), requestTo("https://example.com/hook"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lag")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("orders")))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        new NotificationOutboxService(mapper, settings, silences, alerts, audit, client).dispatch();

        server.verify();
        verify(mapper).update(any(), any());
        verify(audit).record("DELIVER_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=dingtalk", "SUCCESS", null);
    }

    @Test
    void retriesDingTalkDeliveryWhenTheRobotRejectsThePayloadTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        SettingsRepository settings = mock(SettingsRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .dingtalkWebhook("https://example.com/hook").build());
        server.expect(once(), requestTo("https://example.com/hook"))
                .andRespond(withSuccess("{\"errcode\":310000,\"errmsg\":\"keywords not in content\"}",
                        MediaType.APPLICATION_JSON));

        new NotificationOutboxService(mapper, settings, mock(AlertSilenceService.class), alerts, audit, client).dispatch();

        server.verify();
        verify(audit).record("RETRY_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=dingtalk", "RETRYING",
                "DingTalk rejected webhook: keywords not in content");
    }

    @Test
    void treatsPlainTextSuccessFromAnSmsWebhookAsDeliveredTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        SettingsRepository settings = mock(SettingsRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("sms");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .smsWebhook("https://example.com/sms").build());
        server.expect(once(), requestTo("https://example.com/sms"))
                .andRespond(withSuccess("accepted", MediaType.TEXT_PLAIN));

        new NotificationOutboxService(mapper, settings, mock(AlertSilenceService.class), alerts, audit, client).dispatch();

        server.verify();
        verify(audit).record("DELIVER_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=sms", "SUCCESS", null);
    }

    @Test
    void retriesAClaimedDeliveryWhenNoWebhookIsConfiguredTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L).build()));

        new NotificationOutboxService(mapper, mock(SettingsRepository.class), mock(AlertSilenceService.class), alerts,
                audit).dispatch();

        verify(mapper).update(any(), argThat(wrapper -> wrapper != null));
        verify(audit).record("RETRY_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=dingtalk", "RETRYING", "No configured dingtalk webhook");
    }

    @Test
    void dispatchesEmailDeliveryToConfiguredRecipientsTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        SettingsRepository settings = mock(SettingsRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("email");
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
        when(alerts.findAlertById(9L)).thenReturn(Optional.of(SystemAlertVO.builder().id(9L)
                .level(AlertLevel.warning).title("Lag").description("high").instanceId("local").build()));
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .emailRecipients("ops@example.com, oncall@example.com").build());

        new NotificationOutboxService(mapper, settings, mock(AlertSilenceService.class), alerts, audit,
                new RestTemplate(), () -> mailSender).dispatch();

        verify(mailSender).send(argThat((SimpleMailMessage message) -> message.getTo() != null
                && Arrays.equals(message.getTo(), new String[] {"ops@example.com", "oncall@example.com"})
                && "[RocketMQ Studio] Lag".equals(message.getSubject())));
        verify(audit).record("DELIVER_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=email", "SUCCESS", null);
    }

    @Test
    void reclaimsAStaleSendingDeliveryTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus("SENDING");
        row.setSendingStartedAt(LocalDateTime.now().minusMinutes(2));
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class)))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString())).thenReturn(0);

        new NotificationOutboxService(mapper, mock(SettingsRepository.class), mock(AlertSilenceService.class),
                mock(AlertRepository.class), mock(OperationAuditService.class)).dispatch();

        verify(mapper).claimForDispatch(org.mockito.ArgumentMatchers.eq(8L), any(LocalDateTime.class),
                any(LocalDateTime.class), any(LocalDateTime.class), anyString());
    }

    @Test
    void listsAllDeliveryRecordsWithNormalizedFiltersAndPagingTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        NotificationDeliveryPageVO delivery = NotificationDeliveryPageVO.builder().id(8L).alertId(9L)
                .channel("dingtalk").status(NotificationOutboxStatus.DELIVERED).attemptCount(0).build();
        when(mapper.countPage("dingtalk", "DELIVERED", "Local")).thenReturn(1L);
        when(mapper.findPage("dingtalk", "DELIVERED", "Local", 20, 0)).thenReturn(List.of(delivery));

        PageResult<NotificationDeliveryPageVO> result = new NotificationOutboxService(mapper,
                mock(SettingsRepository.class), mock(AlertSilenceService.class), mock(AlertRepository.class),
                mock(OperationAuditService.class)).listDeliveries(" DingTalk ", "delivered", "Local", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).containsExactly(delivery);
    }

    @Test
    void retriesOnlyFailedDeliveryAndResetsItsDispatchStateTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel("dingtalk");
        row.setStatus(NotificationOutboxStatus.FAILED.name());
        when(mapper.selectById(8L)).thenReturn(row);
        when(mapper.update(org.mockito.ArgumentMatchers.isNull(), any(UpdateWrapper.class))).thenReturn(1);

        new NotificationOutboxService(mapper, mock(SettingsRepository.class), mock(AlertSilenceService.class),
                mock(AlertRepository.class), audit).retryFailedDelivery(8L);

        verify(mapper).update(org.mockito.ArgumentMatchers.isNull(), any(UpdateWrapper.class));
        verify(audit).record("RETRY_ALERT_NOTIFICATION_MANUALLY", "ALERT_NOTIFICATION", "8", null,
                "alertId=9, channel=dingtalk", "SUCCESS", null);
    }

    @Test
    void rejectsManualRetryForNonFailedDeliveryTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setStatus(NotificationOutboxStatus.DELIVERED.name());
        when(mapper.selectById(8L)).thenReturn(row);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new NotificationOutboxService(mapper,
                mock(SettingsRepository.class), mock(AlertSilenceService.class), mock(AlertRepository.class),
                mock(OperationAuditService.class)).retryFailedDelivery(8L))
                .isInstanceOf(org.apache.rocketmq.studio.common.exception.BusinessException.class)
                .hasMessage("Only failed notification deliveries can be retried");
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void retriesFailedDeliveriesIndependentlyInBulkTest() {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        RmqAlertNotificationOutbox failed = new RmqAlertNotificationOutbox();
        failed.setId(8L);
        failed.setAlertId(9L);
        failed.setChannel("dingtalk");
        failed.setStatus(NotificationOutboxStatus.FAILED.name());
        when(mapper.selectById(8L)).thenReturn(failed);
        when(mapper.update(org.mockito.ArgumentMatchers.isNull(), any(UpdateWrapper.class))).thenReturn(1);

        NotificationDeliveryBulkRetryResult result = new NotificationOutboxService(mapper,
                mock(SettingsRepository.class), mock(AlertSilenceService.class), mock(AlertRepository.class),
                mock(OperationAuditService.class)).retryFailedDeliveries(List.of(8L, 9L, 8L));

        assertThat(result.getSucceededIds()).containsExactly(8L);
        assertThat(result.getFailures()).containsKey(9L);
    }
}
