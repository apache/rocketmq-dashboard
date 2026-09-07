/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertStateMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlusAlertStateRepositoryTest {
    @Test
    void savesUsingTheStateRevisionSoAnAckCannotBeOverwrittenTest() {
        RmqAlertStateMapper mapper = mock(RmqAlertStateMapper.class);
        RmqAlertState existing = new RmqAlertState();
        existing.setId(9L);
        existing.setVersion(3);
        existing.setStatus(AlertStateStatus.FIRING.name());
        when(mapper.selectOne(any())).thenReturn(existing);
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper,
                mock(RmqSystemAlertMapper.class));

        repository.save(new AlertStateKey(4L, "fingerprint"), new AlertRuleState(AlertStateStatus.FIRING, 2,
                10D, Instant.now(), Instant.now(), Instant.now(), null));

        verify(mapper).updateIfVersion(any(RmqAlertState.class), eq(3));
    }

    @Test
    void acknowledgesWithOneConditionalUpdateTest() {
        RmqAlertStateMapper mapper = mock(RmqAlertStateMapper.class);
        Instant firedAt = Instant.parse("2026-08-22T12:00:00Z");
        when(mapper.acknowledgeFiring(eq(4L), eq("fingerprint"),
                eq(LocalDateTime.ofInstant(firedAt, ZoneOffset.UTC)), any())).thenReturn(1);
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper,
                mock(RmqSystemAlertMapper.class));

        repository.acknowledge(new AlertStateKey(4L, "fingerprint"), firedAt);

        verify(mapper).acknowledgeFiring(eq(4L), eq("fingerprint"),
                eq(LocalDateTime.ofInstant(firedAt, ZoneOffset.UTC)), any());
    }

    @Test
    void findsActiveStatesWithLabelsFromTheLatestAlertEventTest() {
        RmqAlertStateMapper mapper = mock(RmqAlertStateMapper.class);
        RmqAlertState state = new RmqAlertState();
        state.setRuleId(4L);
        state.setFingerprint("fingerprint");
        state.setStatus(AlertStateStatus.ACKED.name());
        state.setConsecutiveHits(1);
        state.setCurrentValue(30D);
        when(mapper.selectList(any())).thenReturn(List.of(state));
        RmqSystemAlert alert = new RmqSystemAlert();
        alert.setRuleId(4L);
        alert.setFingerprint("fingerprint");
        alert.setInstanceId("local");
        alert.setLabelsJson("{\"consumerGroup\":\"orders\"}");
        RmqSystemAlertMapper alertMapper = mock(RmqSystemAlertMapper.class);
        when(alertMapper.selectList(any())).thenReturn(List.of(alert));
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper, alertMapper);
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).domain(AlertDomain.BUSINESS).enabled(true)
                .instanceId("local").metric("consumer.lag.total").build();

        List<ActiveAlertState> active = repository.findActive(new MetricCollectionScope(AlertDomain.BUSINESS,
                "local", Set.of("consumer.lag.total")), List.of(rule));

        assertThat(active).singleElement().satisfies(item -> {
            assertThat(item.key()).isEqualTo(new AlertStateKey(4L, "fingerprint"));
            assertThat(item.state().status()).isEqualTo(AlertStateStatus.ACKED);
            assertThat(item.instanceId()).isEqualTo("local");
            assertThat(item.labels()).isEqualTo(Map.of("consumerGroup", "orders"));
        });
        verify(alertMapper).selectList(any());
        verify(alertMapper, never()).selectOne(any());
    }

    @Test
    void findsActiveStatesWithOneLatestAlertMetadataQueryTest() {
        RmqAlertStateMapper mapper = mock(RmqAlertStateMapper.class);
        RmqAlertState first = activeState(4L, "fingerprint-a", AlertStateStatus.FIRING);
        RmqAlertState second = activeState(5L, "fingerprint-b", AlertStateStatus.ACKED);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        RmqSystemAlertMapper alertMapper = mock(RmqSystemAlertMapper.class);
        RmqSystemAlert firstAlert = alert(4L, "fingerprint-a", "local", "{\"consumerGroup\":\"orders\"}");
        RmqSystemAlert secondAlert = alert(5L, "fingerprint-b", "local", "{\"consumerGroup\":\"payments\"}");
        RmqSystemAlert staleFirstAlert = alert(4L, "fingerprint-a", "local", "{\"consumerGroup\":\"old\"}");
        when(alertMapper.selectList(any())).thenReturn(List.of(firstAlert, secondAlert, staleFirstAlert));
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper, alertMapper);
        AlertRuleVO firstRule = AlertRuleVO.builder().id(4L).domain(AlertDomain.BUSINESS).enabled(true)
                .instanceId("local").metric("consumer.lag.total").build();
        AlertRuleVO secondRule = AlertRuleVO.builder().id(5L).domain(AlertDomain.BUSINESS).enabled(true)
                .instanceId("local").metric("consumer.lag.total").build();

        List<ActiveAlertState> active = repository.findActive(new MetricCollectionScope(AlertDomain.BUSINESS,
                "local", Set.of("consumer.lag.total")), List.of(firstRule, secondRule));

        assertThat(active).hasSize(2);
        assertThat(active).extracting(item -> item.labels().get("consumerGroup"))
                .containsExactly("orders", "payments");
        verify(alertMapper, times(1)).selectList(any());
        verify(alertMapper, never()).selectOne(any());
    }

    private static RmqAlertState activeState(Long ruleId, String fingerprint, AlertStateStatus status) {
        RmqAlertState state = new RmqAlertState();
        state.setRuleId(ruleId);
        state.setFingerprint(fingerprint);
        state.setStatus(status.name());
        state.setConsecutiveHits(1);
        state.setCurrentValue(30D);
        return state;
    }

    private static RmqSystemAlert alert(Long ruleId, String fingerprint, String instanceId, String labelsJson) {
        RmqSystemAlert alert = new RmqSystemAlert();
        alert.setRuleId(ruleId);
        alert.setFingerprint(fingerprint);
        alert.setInstanceId(instanceId);
        alert.setLabelsJson(labelsJson);
        return alert;
    }
}
