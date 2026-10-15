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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "studio.auth.login-required=false",
    "studio.alerting.notification-retention=P36500D"
})
class NotificationOutboxMapperIntegrationTest {
    private static final long ALERT_ID_BASE = 2748000L;
    private static final DateTimeFormatter MYSQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private RmqAlertNotificationOutboxMapper mapper;

    @Autowired
    private RmqSystemAlertMapper alertMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private NotificationOutboxService scheduledDispatcher;

    @Test
    void staleDispatchCannotOverwriteTheRetryCountTest() {
        Long alertId = insertAlert("Stale dispatch regression", "stale-dispatch-" + UUID.randomUUID());
        List<Long> alertIds = List.of(alertId);
        RmqAlertNotificationOutboxMapper dispatcherMapper = spy(mapper);
        SettingsRepository settings = mock(SettingsRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        NotificationOutboxService dispatcher = new NotificationOutboxService(dispatcherMapper, settings,
                mock(AlertSilenceService.class), alerts, audit, new RestTemplate(), () -> {
                    throw new IllegalStateException("Simulated email failure");
                });
        when(settings.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .emailRecipients("ops@example.com").build());
        when(alerts.findAlertById(alertId)).thenReturn(java.util.Optional.of(SystemAlertVO.builder()
                .id(alertId).level(AlertLevel.warning).title("Lag").description("High lag").build()));
        try {
            Long id = insertDeliveryAt(alertId, "email", "Previous failure",
                    LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
            jdbcTemplate.update("UPDATE rmq_alert_notification_outbox SET status = 'RETRY_WAIT', "
                    + "attempt_count = 3, next_attempt_at = ? WHERE id = ?",
                    MYSQL_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)), id);
            AtomicBoolean advanceOnce = new AtomicBoolean();
            doAnswer(invocation -> {
                List<RmqAlertNotificationOutbox> selected = mapper.findDispatchable(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2));
                if (advanceOnce.compareAndSet(false, true)) {
                    assertThat(selected).extracting(RmqAlertNotificationOutbox::getAttemptCount).contains(3);
                    // Another replica fails its fourth attempt after this batch was read.
                    jdbcTemplate.update("UPDATE rmq_alert_notification_outbox SET attempt_count = 4, "
                            + "last_error = 'Other replica failure' WHERE id = ?", id);
                }
                return selected;
            }).when(dispatcherMapper).findDispatchable(any(LocalDateTime.class), any(LocalDateTime.class), anyInt());

            dispatcher.dispatch();

            RmqAlertNotificationOutbox unchanged = mapper.selectById(id);
            assertThat(unchanged.getStatus()).isEqualTo("RETRY_WAIT");
            assertThat(unchanged.getAttemptCount()).isEqualTo(4);
            assertThat(unchanged.getLastError()).isEqualTo("Other replica failure");
            assertThat(unchanged.getClaimToken()).isNull();
            assertThat(unchanged.getSendingStartedAt()).isNull();
            verifyNoInteractions(settings, alerts, audit);

            dispatcher.dispatch();

            RmqAlertNotificationOutbox exhausted = mapper.selectById(id);
            assertThat(exhausted.getAttemptCount()).isEqualTo(5);
            assertThat(exhausted.getStatus()).isEqualTo("FAILED");
            dispatcher.dispatch();
            verify(settings).loadGeneralSettings();
            verify(audit).record("FAIL_ALERT_NOTIFICATION", "ALERT_NOTIFICATION", String.valueOf(id), null,
                    "alertId=" + alertId + ", channel=email", "FAILED", "Simulated email failure");
        } finally {
            dispatcher.closeHeartbeatExecutor();
            cleanup(alertIds);
            alertMapper.deleteById(alertId);
        }
    }

    @Test
    void dispatchClaimStillHonorsFreshAndExpiredLeasesTest() {
        List<Long> alertIds = List.of(ALERT_ID_BASE + 301);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime staleBefore = now.minusMinutes(1);
        cleanup(alertIds);
        try {
            insert(alertIds.get(0), "email", NotificationOutboxStatus.SENDING, null, now);
            Long id = mapper.selectList(new QueryWrapper<RmqAlertNotificationOutbox>()
                    .in("alert_id", alertIds)).get(0).getId();
            jdbcTemplate.update("UPDATE rmq_alert_notification_outbox SET attempt_count = 2, "
                    + "sending_started_at = ? WHERE id = ?", MYSQL_DATE_TIME.format(now.minusSeconds(10)), id);

            assertThat(mapper.claimForDispatch(id, now, staleBefore, now, "new-token", 2)).isZero();
            jdbcTemplate.update("UPDATE rmq_alert_notification_outbox SET sending_started_at = ? WHERE id = ?",
                    MYSQL_DATE_TIME.format(staleBefore.minusSeconds(1)), id);
            assertThat(mapper.claimForDispatch(id, now, staleBefore, now, "stale-count-token", 1)).isZero();
            assertThat(mapper.claimForDispatch(id, now, staleBefore, now, "new-token", 2)).isEqualTo(1);
            assertThat(mapper.selectById(id).getAttemptCount()).isEqualTo(2);
            assertThat(mapper.selectById(id).getClaimToken()).isEqualTo("new-token");
        } finally {
            cleanup(alertIds);
        }
    }

    @Test
    void deliveryPageFiltersSearchAndCreationTimeWithConsistentCountTest() {
        List<Long> alertIds = new ArrayList<>();
        String instanceId = "delivery-filter-test-" + UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 14, 0);
        LocalDateTime end = start.plusHours(1);
        try {
            alertIds.add(insertAlert("Broker DISK % warning", instanceId));
            alertIds.add(insertAlert("Broker unavailable", instanceId));
            alertIds.add(insertAlert("Another broker unavailable", instanceId));
            Long titleMatch = insertDeliveryAt(alertIds.get(0), "email", null, start);
            Long errorMatch = insertDeliveryAt(alertIds.get(1), "email", "Webhook timeout_ABC", end);
            insertDeliveryAt(alertIds.get(2), "email", "Webhook after the incident", end.plusSeconds(1));

            assertThat(mapper.countPage("email", null, instanceId, null, null, null)).isEqualTo(3);
            assertThat(mapper.countPage("email", null, instanceId, "broker", null, null)).isEqualTo(3);
            assertThat(mapper.countPage("email", null, instanceId, null, start, end)).isEqualTo(2);
            assertThat(mapper.countPage("email", null, instanceId, "broker", start, end)).isEqualTo(2);
            assertThat(mapper.findPage("email", null, instanceId, "broker", start, end, 1, 0))
                    .extracting(NotificationDeliveryPageVO::getId).containsExactly(errorMatch);
            assertThat(mapper.findPage("email", null, instanceId, "broker", start, end, 1, 1))
                    .extracting(NotificationDeliveryPageVO::getId).containsExactly(titleMatch);

            assertThat(mapper.countPage(null, null, instanceId, "webhook", start, end)).isEqualTo(1);
            assertThat(mapper.findPage(null, null, instanceId, "WEBHOOK", start, end, 10, 0))
                    .extracting(NotificationDeliveryPageVO::getId).containsExactly(errorMatch);
            assertThat(mapper.countPage(null, null, instanceId, "%", null, null)).isEqualTo(1);
            assertThat(mapper.countPage(null, null, instanceId, "_ABC", null, null)).isEqualTo(1);
            assertThat(mapper.countPage(null, null, instanceId, "webhook", end.plusSeconds(1), null)).isEqualTo(1);
        } finally {
            cleanup(alertIds);
            if (!alertIds.isEmpty()) {
                alertMapper.delete(new QueryWrapper<RmqSystemAlert>().in("id", alertIds));
            }
        }
    }

    private Long insertAlert(String title, String instanceId) {
        RmqSystemAlert alert = new RmqSystemAlert();
        alert.setTitle(title);
        alert.setInstanceId(instanceId);
        alertMapper.insert(alert);
        return alert.getId();
    }

    private Long insertDeliveryAt(Long alertId, String channel, String error, LocalDateTime createdAt) {
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setAlertId(alertId);
        row.setChannel(channel);
        row.setStatus(NotificationOutboxStatus.FAILED.name());
        row.setAttemptCount(1);
        row.setNextAttemptAt(createdAt);
        row.setLastError(error);
        mapper.insert(row);
        jdbcTemplate.update("UPDATE rmq_alert_notification_outbox SET gmt_create = ? WHERE id = ?",
                MYSQL_DATE_TIME.format(createdAt), row.getId());
        return row.getId();
    }

    @Test
    void deleteTerminalBeforeShouldDeleteOnlyExpiredDeliveredAndFailedRowsTest() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expired = now.minusDays(31);
        LocalDateTime fresh = now.minusDays(1);
        List<Long> alertIds = List.of(ALERT_ID_BASE + 1, ALERT_ID_BASE + 2, ALERT_ID_BASE + 3, ALERT_ID_BASE + 4,
                ALERT_ID_BASE + 5, ALERT_ID_BASE + 6, ALERT_ID_BASE + 7);
        cleanup(alertIds);
        try {
            insert(alertIds.get(0), "dingtalk", NotificationOutboxStatus.DELIVERED, expired, expired);
            insert(alertIds.get(1), "dingtalk", NotificationOutboxStatus.FAILED, null, expired);
            insert(alertIds.get(2), "dingtalk", NotificationOutboxStatus.PENDING, null, expired);
            insert(alertIds.get(3), "dingtalk", NotificationOutboxStatus.RETRY_WAIT, null, expired);
            insert(alertIds.get(4), "dingtalk", NotificationOutboxStatus.SENDING, null, expired);
            insert(alertIds.get(5), "dingtalk", NotificationOutboxStatus.DELIVERED, fresh, fresh);
            insert(alertIds.get(6), "dingtalk", NotificationOutboxStatus.DELIVERED, null, expired);

            assertThat(mapper.deleteTerminalBefore(now.minusDays(30), 10)).isEqualTo(3);

            assertThat(mapper.selectList(new QueryWrapper<RmqAlertNotificationOutbox>()
                    .in("alert_id", alertIds)).stream().map(RmqAlertNotificationOutbox::getStatus))
                    .containsExactlyInAnyOrder(NotificationOutboxStatus.PENDING.name(),
                            NotificationOutboxStatus.RETRY_WAIT.name(), NotificationOutboxStatus.SENDING.name(),
                            NotificationOutboxStatus.DELIVERED.name());
        } finally {
            cleanup(alertIds);
        }
    }

    @Test
    void claimAndRenewStampGmtModifiedFromTheCallerClockTest() {
        List<Long> alertIds = List.of(ALERT_ID_BASE + 201);
        cleanup(alertIds);
        try {
            insert(alertIds.get(0), "dingtalk", NotificationOutboxStatus.PENDING, null, LocalDateTime.now());
            Long id = mapper.selectList(new QueryWrapper<RmqAlertNotificationOutbox>()
                    .in("alert_id", alertIds)).get(0).getId();
            // Whole-second values: the datetime column drops sub-second precision on read-back.
            LocalDateTime claimedAt = LocalDateTime.of(2026, 9, 30, 8, 0, 0);
            LocalDateTime renewedAt = LocalDateTime.of(2026, 9, 30, 8, 0, 20);

            // The claims carry the dispatch clock. Without an explicit gmt_modified the column falls
            // back to ON UPDATE CURRENT_TIMESTAMP - the database session clock, not the caller's.
            // The due window looks a minute ahead: datetime rounds the insert timestamp up to the
            // whole second, so a wall-clock "now" can fall a fraction before the stored value.
            LocalDateTime claimNow = LocalDateTime.now().plusMinutes(1);
            assertThat(mapper.claimForDispatch(id, claimNow, claimNow.minusMinutes(5),
                    claimedAt, "token-a", 0)).isEqualTo(1);
            assertThat(mapper.selectById(id).getGmtModified()).isEqualTo(claimedAt);

            assertThat(mapper.renewClaim(id, "token-a", renewedAt)).isEqualTo(1);
            assertThat(mapper.selectById(id).getGmtModified()).isEqualTo(renewedAt);
        } finally {
            cleanup(alertIds);
        }
    }

    @Test
    void deleteTerminalBeforeShouldRespectBatchLimitTest() {
        LocalDateTime expired = LocalDateTime.now().minusDays(31);
        List<Long> alertIds = List.of(ALERT_ID_BASE + 101, ALERT_ID_BASE + 102, ALERT_ID_BASE + 103);
        cleanup(alertIds);
        try {
            insert(alertIds.get(0), "email", NotificationOutboxStatus.FAILED, null, expired);
            insert(alertIds.get(1), "email", NotificationOutboxStatus.FAILED, null, expired);
            insert(alertIds.get(2), "email", NotificationOutboxStatus.FAILED, null, expired);

            assertThat(mapper.deleteTerminalBefore(LocalDateTime.now().minusDays(30), 2)).isEqualTo(2);
            assertThat(mapper.selectCount(new QueryWrapper<RmqAlertNotificationOutbox>()
                    .in("alert_id", alertIds))).isEqualTo(1);
        } finally {
            cleanup(alertIds);
        }
    }

    private void insert(Long alertId, String channel, NotificationOutboxStatus status, LocalDateTime deliveredAt,
            LocalDateTime modifiedAt) {
        RmqAlertNotificationOutbox row = new RmqAlertNotificationOutbox();
        row.setAlertId(alertId);
        row.setChannel(channel);
        row.setStatus(status.name());
        row.setAttemptCount(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setDeliveredAt(deliveredAt);
        row.setGmtModified(modifiedAt);
        mapper.insert(row);
    }

    private void cleanup(List<Long> alertIds) {
        if (!alertIds.isEmpty()) {
            mapper.delete(new QueryWrapper<RmqAlertNotificationOutbox>().in("alert_id", alertIds));
        }
    }
}
