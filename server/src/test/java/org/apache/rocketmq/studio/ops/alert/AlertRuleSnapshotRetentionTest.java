/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import org.apache.rocketmq.studio.cluster.metrics.MetricProfileService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertRuleSnapshotRetentionTest {

    @Test
    void rejectsCreateWhenNativeAggregationWindowExceedsSnapshotRetention() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.insertRule(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AlertService service = alertService("PT24H", repository);

        assertThatThrownBy(() -> service.createRule(nativeRule(null, 86_401)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("snapshot retention")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(repository, never()).insertRule(any());
    }

    @Test
    void rejectsUpdateWhenNativeAggregationWindowExceedsSnapshotRetention() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.replaceRule(any())).thenReturn(true);
        AlertService service = alertService("PT24H", repository);

        assertThatThrownBy(() -> service.updateRule(nativeRule(1L, 86_401)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("snapshot retention")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(repository, never()).replaceRule(any());
    }

    @Test
    void acceptsNativeAggregationWindowEqualToSnapshotRetention() {
        AlertRepository repository = acceptingRepository();
        AlertService service = alertService("PT1H", repository);

        assertThatCode(() -> service.createRule(nativeRule(null, 3_600))).doesNotThrowAnyException();

        verify(repository).insertRule(any());
    }

    @Test
    void acceptsSingleSampleNativeRuleWithPositiveSnapshotRetention() {
        AlertRepository repository = acceptingRepository();
        AlertService service = alertService("PT1H", repository);

        assertThatCode(() -> service.createRule(nativeRule(null, 0))).doesNotThrowAnyException();

        verify(repository).insertRule(any());
    }

    @Test
    void leavesNonNativePrometheusRuleIndependentOfSnapshotRetention() {
        AlertRepository repository = acceptingRepository();
        AlertRuleVO rule = nativeRule(null, 7_200);
        rule.setMetric("rocketmq_consumer_lag_messages");
        rule.setInstanceId(null);
        rule.setConsumerGroup(null);
        AlertService service = alertService("PT1H", repository);

        assertThatCode(() -> service.createRule(rule)).doesNotThrowAnyException();

        verify(repository).insertRule(rule);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1H"})
    void acceptsNativeAggregationWindowWhenSnapshotCleanupIsDisabled(String retention) {
        AlertRepository repository = acceptingRepository();
        AlertService service = alertService(retention, repository);

        assertThatCode(() -> service.createRule(nativeRule(null, 172_800))).doesNotThrowAnyException();

        verify(repository).insertRule(any());
    }

    private static AlertRuleVO nativeRule(Long id, int windowSeconds) {
        return AlertRuleVO.builder()
                .id(id)
                .domain(AlertDomain.BUSINESS)
                .name("Consumer lag average")
                .metric("consumer.lag.total")
                .operator(">")
                .threshold(1000)
                .duration("5m")
                .aggregation("AVG")
                .windowSeconds(windowSeconds)
                .instanceId("instance-a")
                .consumerGroup("group-a")
                .build();
    }

    private static AlertRepository acceptingRepository() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.insertRule(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private static AlertService alertService(String retention, AlertRepository repository) {
        AlertingProperties properties = new AlertingProperties();
        properties.setSnapshotRetention(retention);
        return new AlertService(repository, mock(AlertStateRepository.class), new AlertRuleAssetService(),
                mock(OperationAuditService.class), mock(MetricProfileService.class), properties);
    }
}
