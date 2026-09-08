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
import org.apache.rocketmq.studio.persistence.entity.RmqAlertNotificationOutbox;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises create -> normalized storage -> schedule evaluation -> outbox enqueue. */
class RecurringSilenceNotificationTest {

    @ParameterizedTest
    @CsvSource({
        "2026-03-07T02:30:00-05:00, 2026-03-07T04:00:00-05:00, "
                + "2026-03-08T03:45:00-04:00, 2026-03-08T04:00:00-04:00",
        "2026-11-01T01:15:00-05:00, 2026-11-01T01:45:00-05:00, "
                + "2026-11-01T01:30:00-05:00, 2026-11-01T01:45:00-05:00",
        "2026-11-01T00:30:00-04:00, 2026-11-01T01:45:00-05:00, "
                + "2026-11-01T01:30:00-05:00, 2026-11-01T01:45:00-05:00"
    })
    void createdWindowDefersEveryChannelToTheExactEndTest(
            String start, String end, String eventTime, String expectedEnd) {
        AlertSilenceRepository repository = mock(AlertSilenceRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        AlertSilenceService silences = new AlertSilenceService(repository, audit);
        when(repository.save(any())).thenAnswer(invocation -> {
            AlertSilenceVO stored = invocation.getArgument(0);
            stored.setId(11L);
            return stored;
        });

        CreateAlertSilenceDTO request = request(start, end);
        AlertSilenceVO saved = silences.create(request);
        assertThat(saved.getStartsAt()).isEqualTo(utc(start));
        assertThat(saved.getEndsAt()).isEqualTo(utc(end));
        assertThat(saved.getLabels()).containsEntry("brokerName", "broker-a");
        verify(audit).record(eq("CREATE_ALERT_SILENCE"), eq("ALERT_SILENCE"), eq("11"),
                eq("local"), any(), eq("SUCCESS"), eq(null));

        LocalDateTime now = utc(eventTime);
        when(repository.findActiveCandidates(AlertDomain.CLUSTER, 4L, "local", now))
                .thenReturn(List.of(saved));
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).domain(AlertDomain.CLUSTER)
                .channels(List.of("email", "dingtalk", "sms")).build();
        SystemAlertVO alert = SystemAlertVO.builder().id(9L).domain(AlertDomain.CLUSTER)
                .title("Disk usage").instanceId("local").time(now).build();
        Map<String, String> labels = Map.of("brokerName", "broker-a");
        RmqAlertNotificationOutboxMapper outbox = mock(RmqAlertNotificationOutboxMapper.class);
        NotificationOutboxService notifications = new NotificationOutboxService(outbox,
                mock(SettingsRepository.class), silences, mock(AlertRepository.class), audit);

        try {
            notifications.enqueue(alert, rule, labels);

            ArgumentCaptor<RmqAlertNotificationOutbox> rows =
                    ArgumentCaptor.forClass(RmqAlertNotificationOutbox.class);
            verify(outbox, times(3)).insert(rows.capture());
            assertThat(rows.getAllValues()).extracting(RmqAlertNotificationOutbox::getChannel)
                    .containsExactly("email", "dingtalk", "sms");
            assertThat(rows.getAllValues()).allSatisfy(row -> {
                assertThat(row.getNextAttemptAt()).isEqualTo(utc(expectedEnd));
                assertThat(row.getStatus()).isEqualTo("PENDING");
                assertThat(row.getAttemptCount()).isZero();
                assertThat(row.getAlertId()).isEqualTo(9L);
            });
        } finally {
            notifications.closeHeartbeatExecutor();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "2026-03-07T02:30:00-05:00, 2026-03-07T04:00:00-05:00, 2026-03-08T04:15:00-04:00",
        "2026-11-01T01:15:00-05:00, 2026-11-01T01:45:00-05:00, 2026-11-01T01:30:00-04:00",
        "2026-11-01T01:15:00-05:00, 2026-11-01T01:45:00-05:00, 2026-11-01T01:45:00-05:00"
    })
    void eventsOutsideTheActualWindowRemainDeliverableTest(String start, String end, String eventTime) {
        AlertSilenceRepository repository = mock(AlertSilenceRepository.class);
        AlertSilenceService silences = new AlertSilenceService(repository, mock(OperationAuditService.class));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AlertSilenceVO saved = silences.create(request(start, end));
        LocalDateTime now = utc(eventTime);
        when(repository.findActiveCandidates(AlertDomain.CLUSTER, 4L, "local", now))
                .thenReturn(List.of(saved));
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).domain(AlertDomain.CLUSTER).build();

        assertThat(silences.activeUntil(rule, "local", Map.of("brokerName", "broker-a"), now))
                .isNull();
    }

    private static CreateAlertSilenceDTO request(String start, String end) {
        CreateAlertSilenceDTO request = new CreateAlertSilenceDTO();
        request.setDomain(AlertDomain.CLUSTER);
        request.setRuleId(4L);
        request.setInstanceId("local");
        request.setLabels(Map.of("brokerName", "broker-a"));
        request.setStartsAt(OffsetDateTime.parse(start));
        request.setEndsAt(OffsetDateTime.parse(end));
        request.setRecurrence(AlertSilenceRecurrence.DAILY);
        request.setTimeZone("America/New_York");
        request.setRecurrenceUntil(OffsetDateTime.parse(end).plusDays(4));
        request.setReason("Broker maintenance");
        return request;
    }

    private static LocalDateTime utc(String value) {
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
