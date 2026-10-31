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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Runs with the CI MySQL datasource, or with the dev/H2 profile for local verification. */
@SpringBootTest(properties = "studio.alerting.notification-dispatch-interval=PT24H")
class AlertRuleEvaluationConcurrencyTest {
    @Autowired
    private AlertService alerts;
    @Autowired
    private NativeAlertEvaluationService evaluation;
    @Autowired
    private NativeAlertProcessor processor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private MetricSnapshotRepository snapshots;
    @MockitoSpyBean
    private AlertRepository repository;
    @MockitoSpyBean
    private AlertStateRepository states;
    private AlertRuleVO rule;
    private MetricSample sample;

    @BeforeEach
    void initializeTest() {
        String group = "race-" + UUID.randomUUID();
        rule = alerts.createRule(AlertRuleVO.builder().name(group).domain(AlertDomain.BUSINESS)
                .metric("consumer.lag.total").operator(">").threshold(10).instanceId("probe-instance")
                .consumerGroup(group).enabled(true).windowSeconds(60).channels(List.of("email")).build());
        sample = new MetricSample(rule.getMetric(), AlertDomain.BUSINESS, rule.getInstanceId(), null,
                Map.of("consumerGroup", group), 20D, MetricAvailability.AVAILABLE, Instant.now());
        when(snapshots.findRecent(any(), any())).thenReturn(List.of(sample));
    }

    @AfterEach
    void cleanupTest() {
        if (rule == null) {
            return;
        }
        jdbc.update("DELETE FROM rmq_alert_notification_outbox WHERE alert_id IN "
                + "(SELECT id FROM rmq_system_alert WHERE rule_id=?)", rule.getId());
        jdbc.update("DELETE FROM rmq_system_alert WHERE rule_id=?", rule.getId());
        jdbc.update("DELETE FROM rmq_alert_state WHERE rule_id=?", rule.getId());
        jdbc.update("DELETE FROM rmq_alert_rule WHERE id=?", rule.getId());
    }

    private String databaseProduct() {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
    }

    private long sessionId() {
        return jdbc.queryForObject("MySQL".equals(databaseProduct())
                ? "SELECT CONNECTION_ID()" : "SELECT SESSION_ID()", Long.class);
    }

    /**
     * Observes the management write actually waiting on the evaluation's row lock, instead of
     * inferring it from "still unfinished after a fixed delay" - a slow runner would pass such an
     * assertion without ever reaching the statement. MySQL reports the wait in
     * information_schema.innodb_trx, the dev/H2 profile in INFORMATION_SCHEMA.SESSIONS, so the
     * check stays deterministic on both datasources and is skipped on neither.
     */
    private boolean awaitManagementLockWait(long timeoutMillis) throws InterruptedException {
        String waitingSessions = "MySQL".equals(databaseProduct())
                ? "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state = 'LOCK WAIT'"
                : "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_STATE = 'BLOCKED'";
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(waitingSessions, Integer.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private void assertRows(int expectedStates, int expectedEvents) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_alert_state WHERE rule_id=?",
                Integer.class, rule.getId())).isEqualTo(expectedStates);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_system_alert WHERE rule_id=?",
                Integer.class, rule.getId())).isEqualTo(expectedEvents);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_alert_notification_outbox WHERE alert_id IN "
                + "(SELECT id FROM rmq_system_alert WHERE rule_id=?)", Integer.class, rule.getId()))
                .isEqualTo(expectedEvents);
    }

    private void afterPausedEvaluation(Consumer<AlertRuleVO> mutation, int expectedRows) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicReference<Long> evaluationSession = new AtomicReference<>();
        when(snapshots.findRecent(any(), any())).thenAnswer(inv -> {
            evaluationSession.set(sessionId());
            // Establish a real MVCC snapshot before the management transaction commits.
            assertThat(repository.findRuleById(rule.getId()).orElseThrow().isEnabled()).isTrue();
            started.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return List.of(sample);
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> evaluation.evaluate(rule, sample));
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                Long controlSession = new TransactionTemplate(transactionManager).execute(status -> {
                    long id = sessionId();
                    mutation.accept(repository.findRuleById(rule.getId()).orElseThrow());
                    return id;
                });
                assertThat(controlSession).isNotEqualTo(evaluationSession.get());
                resume.countDown();
                pending.get(10, TimeUnit.SECONDS);
                assertRows(expectedRows, expectedRows);
            } finally {
                resume.countDown();
            }
        }
    }

    @Test
    void committedDisableShouldRejectOldEvaluationTest() throws Exception {
        afterPausedEvaluation(current -> alerts.toggleRule(AlertDomain.BUSINESS, current.getId(), false), 0);
        assertThat(repository.findRuleById(rule.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void committedDeleteShouldRejectOldEvaluationTest() throws Exception {
        afterPausedEvaluation(current -> alerts.deleteRule(AlertDomain.BUSINESS, current.getId()), 0);
        assertThat(repository.findRuleById(rule.getId())).isEmpty();
    }

    @Test
    void changedConditionsShouldRejectOldEvaluationTest() throws Exception {
        afterPausedEvaluation(current -> {
            current.setThreshold(100);
            alerts.updateRule(AlertDomain.BUSINESS, current);
        }, 0);
    }

    @Test
    void presentationEditShouldUseCurrentNotificationFieldsTest() throws Exception {
        afterPausedEvaluation(current -> {
            current.setName("Updated presentation");
            current.setNotificationTemplate("updated-template");
            alerts.updateRule(AlertDomain.BUSINESS, current);
        }, 1);
        assertThat(jdbc.queryForObject("SELECT title FROM rmq_system_alert WHERE rule_id=?",
                String.class, rule.getId())).isEqualTo("Updated presentation");
        assertThat(jdbc.queryForObject("SELECT message_content FROM rmq_alert_notification_outbox WHERE alert_id IN "
                + "(SELECT id FROM rmq_system_alert WHERE rule_id=?)", String.class, rule.getId()))
                .isEqualTo("updated-template");
    }

    @Test
    void disableShouldWaitForLockedEvaluationAndThenPurgeItsStateTest() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch managementStarted = new CountDownLatch(1);
        doAnswer(inv -> {
            Object current = inv.callRealMethod();
            locked.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return current;
        }).when((AlertRepository) AopTestUtils.getUltimateTargetObject(repository))
                .findRuleByIdForUpdate(rule.getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var pending = executor.submit(() -> evaluation.evaluate(rule, sample));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var disable = executor.submit(() -> {
                    managementStarted.countDown();
                    alerts.toggleRule(AlertDomain.BUSINESS, rule.getId(), false);
                });
                assertThat(managementStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(awaitManagementLockWait(TimeUnit.SECONDS.toMillis(10))).isTrue();
                resume.countDown();
                pending.get(10, TimeUnit.SECONDS);
                disable.get(10, TimeUnit.SECONDS);
                assertRows(0, 1); // Existing events/outbox are history; disabling only purges runtime state.
                assertThat(repository.findRuleById(rule.getId()).orElseThrow().isEnabled()).isFalse();
            } finally {
                resume.countDown();
            }
        }
    }

    @Test
    void committedDisableShouldRejectStaleMissingStateReconciliationTest() throws Exception {
        evaluation.evaluate(rule, sample);
        assertRows(1, 1);
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(inv -> {
            Object active = inv.callRealMethod();
            read.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return active;
        }).when((AlertStateRepository) AopTestUtils.getUltimateTargetObject(states)).findActive(any(), any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> processor.processSuccessfulCollection(new MetricCollectionScope(
                    AlertDomain.BUSINESS, rule.getInstanceId(), java.util.Set.of(rule.getMetric())), List.of()));
            try {
                assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
                alerts.toggleRule(AlertDomain.BUSINESS, rule.getId(), false);
                resume.countDown();
                pending.get(10, TimeUnit.SECONDS);
                assertRows(0, 1);
            } finally {
                resume.countDown();
            }
        }
    }
}
