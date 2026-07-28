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
package com.rocketmq.studio.ops.alert;

import com.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAlertRepositoryTest {

    private InMemoryAlertRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAlertRepository();
    }

    @Test
    void saveAlertShouldStoreAndReturnAlert() {
        SystemAlertVO alert = SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();

        SystemAlertVO saved = repository.saveAlert(alert);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo("a1");
        assertThat(saved.getTitle()).isEqualTo("Broker Down");
    }

    @Test
    void findAlertsShouldReturnAllWhenLevelIsNull() {
        repository.saveAlert(SystemAlertVO.builder().id("a1").level(AlertLevel.error).title("Alert1").build());
        repository.saveAlert(SystemAlertVO.builder().id("a2").level(AlertLevel.warning).title("Alert2").build());

        List<SystemAlertVO> alerts = repository.findAlerts(null);

        assertThat(alerts).hasSize(2);
    }

    @Test
    void findAlertsShouldFilterByLevelIgnoringCase() {
        repository.saveAlert(SystemAlertVO.builder().id("a1").level(AlertLevel.error).title("Alert1").build());
        repository.saveAlert(SystemAlertVO.builder().id("a2").level(AlertLevel.warning).title("Alert2").build());

        List<SystemAlertVO> alerts = repository.findAlerts("ERROR");

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getId()).isEqualTo("a1");
    }

    @Test
    void findAlertsShouldReturnEmptyListWhenNoAlerts() {
        List<SystemAlertVO> alerts = repository.findAlerts(null);

        assertThat(alerts).isEmpty();
    }

    @Test
    void saveAlertShouldUpdateExistingAlert() {
        repository.saveAlert(SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Original").acknowledged(false).build());

        repository.saveAlert(SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Original").acknowledged(true).build());

        List<SystemAlertVO> alerts = repository.findAlerts(null);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).isAcknowledged()).isTrue();
    }

    @Test
    void deleteAcknowledgedAlertsShouldRemoveOnlyAcknowledged() {
        repository.saveAlert(SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Alert1").acknowledged(true).build());
        repository.saveAlert(SystemAlertVO.builder().id("a2").level(AlertLevel.warning)
                .title("Alert2").acknowledged(false).build());

        int cleared = repository.deleteAcknowledgedAlerts();

        assertThat(cleared).isEqualTo(1);
        List<SystemAlertVO> remaining = repository.findAlerts(null);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo("a2");
    }

    @Test
    void deleteAcknowledgedAlertsShouldReturnZeroWhenNoneAcknowledged() {
        repository.saveAlert(SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Alert1").acknowledged(false).build());

        int cleared = repository.deleteAcknowledgedAlerts();

        assertThat(cleared).isZero();
        assertThat(repository.findAlerts(null)).hasSize(1);
    }
}
