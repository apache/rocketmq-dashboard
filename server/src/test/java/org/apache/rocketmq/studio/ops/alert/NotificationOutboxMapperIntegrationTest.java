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
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "studio.auth.login-required=false")
class NotificationOutboxMapperIntegrationTest {
    private static final long ALERT_ID_BASE = 2748000L;

    @Autowired
    private RmqAlertNotificationOutboxMapper mapper;

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
        mapper.delete(new QueryWrapper<RmqAlertNotificationOutbox>().in("alert_id", alertIds));
    }
}
