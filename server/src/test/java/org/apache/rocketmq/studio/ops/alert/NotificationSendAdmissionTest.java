/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.metrics.AlertingProperties;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSendAdmissionTest {
    private static final String WEBHOOK = "https://192.0.2.1/notification";

    static Stream<Arguments> preparationCases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String channel : List.of("email", "dingtalk", "sms")) {
            for (String checkpoint : List.of("ALERT", "SILENCE", "SETTINGS")) {
                cases.add(Arguments.of(channel, checkpoint, false));
                cases.add(Arguments.of(channel, checkpoint, true));
            }
        }
        cases.add(Arguments.of("email", "MAIL_SENDER", false));
        cases.add(Arguments.of("email", "MAIL_SENDER", true));
        return cases.build();
    }

    @ParameterizedTest(name = "{0} preparation={1}, leaseOwned={2}")
    @MethodSource("preparationCases")
    void checksLeaseBeforeExternalSendAfterPreparationTest(String channel, String checkpoint,
            boolean leaseOwned) throws Exception {
        RmqAlertNotificationOutboxMapper mapper = mock(RmqAlertNotificationOutboxMapper.class);
        SettingsRepository settings = mock(SettingsRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        AlertSilenceService silences = mock(AlertSilenceService.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        ScheduledExecutorService heartbeatExecutor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> renewal = new AtomicReference<>();
        when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> {
                    renewal.set(invocation.getArgument(0));
                    return heartbeatFuture;
                });
        when(mapper.renewClaim(eq(8L), anyString(), any(LocalDateTime.class))).thenReturn(leaseOwned ? 1 : 0);
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setId(8L);
        row.setAlertId(9L);
        row.setChannel(channel);
        row.setStatus("PENDING");
        row.setAttemptCount(0);
        when(mapper.findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row));
        when(mapper.claimForDispatch(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString(), anyInt())).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);

        CountDownLatch preparationEntered = new CountDownLatch(1);
        CountDownLatch releasePreparation = new CountDownLatch(1);
        SystemAlertVO alert = SystemAlertVO.builder().id(9L).level(AlertLevel.warning)
                .title("Lag").description("high").instanceId("local").build();
        when(alerts.findAlertById(9L)).thenAnswer(invocation -> {
            blockAt("ALERT", checkpoint, preparationEntered, releasePreparation);
            return Optional.of(alert);
        });
        when(silences.activeUntil(any(AlertRuleVO.class), eq("local"), any(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    blockAt("SILENCE", checkpoint, preparationEntered, releasePreparation);
                    return null;
                });
        when(settings.loadGeneralSettings()).thenAnswer(invocation -> {
            blockAt("SETTINGS", checkpoint, preparationEntered, releasePreparation);
            return GeneralSettingsVO.builder().emailRecipients("ops@example.com")
                    .dingtalkWebhook(WEBHOOK).smsWebhook(WEBHOOK).build();
        });
        AtomicInteger externalCalls = new AtomicInteger();
        JavaMailSender sender = mock(JavaMailSender.class);
        doAnswer(invocation -> {
            externalCalls.incrementAndGet();
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        RestTemplate client = mock(RestTemplate.class);
        when(client.postForEntity(eq(URI.create(WEBHOOK)), any(), eq(String.class))).thenAnswer(invocation -> {
            externalCalls.incrementAndGet();
            return ResponseEntity.ok("{\"errcode\":0}");
        });
        NotificationOutboxService service = new NotificationOutboxService(mapper, settings, silences,
                alerts, audit, client, () -> {
                    blockAt("MAIL_SENDER", checkpoint, preparationEntered, releasePreparation);
                    return sender;
                }, new AlertingProperties(), heartbeatExecutor);
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        try {
            Future<?> dispatch = dispatcher.submit(service::dispatch);
            assertThat(preparationEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(renewal.get()).isNotNull();
            assertThat(externalCalls).hasValue(0);
            renewal.get().run();
            releasePreparation.countDown();
            dispatch.get(5, TimeUnit.SECONDS);
            assertThat(externalCalls).hasValue(leaseOwned ? 1 : 0);
            verify(mapper, times(leaseOwned ? 1 : 0)).update(any(), any());
            verify(audit, times(leaseOwned ? 1 : 0)).record(any(), any(), any(), any(), any(), any(), any());
            verify(mapper).renewClaim(eq(8L), anyString(), any(LocalDateTime.class));
            verify(heartbeatFuture).cancel(false);
        } finally {
            releasePreparation.countDown();
            dispatcher.shutdownNow();
            assertThat(dispatcher.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            service.closeHeartbeatExecutor();
        }
    }

    private static void blockAt(String current, String selected, CountDownLatch entered, CountDownLatch release) {
        if (current.equals(selected)) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Preparation release timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Preparation interrupted", interrupted);
            }
        }
    }
}
