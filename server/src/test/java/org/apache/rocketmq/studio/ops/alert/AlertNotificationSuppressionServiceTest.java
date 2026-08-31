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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertNotificationSuppressionServiceTest {

    @Test
    void findsAnActiveClusterIncidentInTheSameResourceScopeTest() {
        AlertRepository repository = mock(AlertRepository.class);
        LocalDateTime now = LocalDateTime.now();
        SystemAlertVO cause = event(1L, AlertDomain.CLUSTER, "FIRING", "broker-1", now.minusMinutes(15));
        when(repository.findAlertsPage(any())).thenReturn(PageResult.of(List.of(cause), 1, 1, 100));

        SystemAlertVO result = new AlertNotificationSuppressionService(repository)
                .findSuppressingClusterAlert(event(2L, AlertDomain.BUSINESS, "FIRING", "broker-1", now))
                .orElseThrow();

        assertThat(result.getId()).isEqualTo(1L);
        verify(repository).findAlertsPage(org.mockito.ArgumentMatchers.argThat(query -> query.from().equals(now.minusMinutes(30))
                && query.to().equals(now) && query.domain() == AlertDomain.CLUSTER));
    }

    @Test
    void doesNotSuppressForAnIncidentOutsideTheCorrelationWindowTest() {
        AlertRepository repository = mock(AlertRepository.class);
        LocalDateTime now = LocalDateTime.now();
        when(repository.findAlertsPage(any())).thenReturn(PageResult.of(List.of(), 0, 1, 100));

        assertThat(new AlertNotificationSuppressionService(repository)
                .findSuppressingClusterAlert(event(2L, AlertDomain.BUSINESS, "FIRING", "broker-1", now)))
                .isEmpty();
        verify(repository).findAlertsPage(org.mockito.ArgumentMatchers.argThat(query -> query.from().equals(now.minusMinutes(30))
                && query.to().equals(now) && query.domain() == AlertDomain.CLUSTER));
    }

    @Test
    void doesNotSuppressAfterTheSameClusterIncidentHasResolvedTest() {
        AlertRepository repository = mock(AlertRepository.class);
        LocalDateTime now = LocalDateTime.now();
        SystemAlertVO firing = event(1L, AlertDomain.CLUSTER, "FIRING", "broker-1", now.minusMinutes(3));
        firing.setFingerprint("broker-1-availability");
        SystemAlertVO resolved = event(2L, AlertDomain.CLUSTER, "RESOLVED", "broker-1", now.minusMinutes(1));
        resolved.setFingerprint("broker-1-availability");
        when(repository.findAlertsPage(any())).thenReturn(PageResult.of(List.of(firing, resolved), 2, 1, 100));

        assertThat(new AlertNotificationSuppressionService(repository)
                .findSuppressingClusterAlert(event(3L, AlertDomain.BUSINESS, "FIRING", "broker-1", now)))
                .isEmpty();
    }

    @Test
    void doesNotSuppressAcrossDifferentBrokerScopesTest() {
        AlertRepository repository = mock(AlertRepository.class);
        LocalDateTime now = LocalDateTime.now();
        when(repository.findAlertsPage(any())).thenReturn(PageResult.of(
                List.of(event(1L, AlertDomain.CLUSTER, "FIRING", "broker-2", now.minusMinutes(1))), 1, 1, 100));

        assertThat(new AlertNotificationSuppressionService(repository)
                .findSuppressingClusterAlert(event(2L, AlertDomain.BUSINESS, "FIRING", "broker-1", now)))
                .isEmpty();
    }

    @Test
    void searchesBeyondTheFirstCandidatePageTest() {
        AlertRepository repository = mock(AlertRepository.class);
        LocalDateTime now = LocalDateTime.now();
        List<SystemAlertVO> unrelated = IntStream.range(0, 100)
                .mapToObj(index -> event((long) index, AlertDomain.CLUSTER, "FIRING",
                        "broker-" + index, now.minusMinutes(1)))
                .toList();
        SystemAlertVO cause = event(101L, AlertDomain.CLUSTER, "FIRING", "target-broker",
                now.minusMinutes(2));
        when(repository.findAlertsPage(any())).thenReturn(
                PageResult.of(unrelated, 101, 1, 100),
                PageResult.of(List.of(cause), 101, 2, 100));

        Optional<SystemAlertVO> result = new AlertNotificationSuppressionService(repository)
                .findSuppressingClusterAlert(event(102L, AlertDomain.BUSINESS, "FIRING",
                        "target-broker", now));

        assertThat(result).contains(cause);
        verify(repository, times(2)).findAlertsPage(any());
        verify(repository).findAlertsPage(org.mockito.ArgumentMatchers.argThat(query -> query.page() == 2));
    }

    private static SystemAlertVO event(Long id, AlertDomain domain, String transition, String brokerName,
            LocalDateTime time) {
        return SystemAlertVO.builder().id(id).domain(domain).transition(transition).instanceId("local")
                .title("alert-" + id).time(time).labels(Map.of("brokerName", brokerName)).build();
    }
}
