/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertStateMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper);

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
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper);

        repository.acknowledge(new AlertStateKey(4L, "fingerprint"), firedAt);

        verify(mapper).acknowledgeFiring(eq(4L), eq("fingerprint"),
                eq(LocalDateTime.ofInstant(firedAt, ZoneOffset.UTC)), any());
    }

    @Test
    void findToleratesUnknownStoredStatusValuesTest() {
        RmqAlertStateMapper mapper = mock(RmqAlertStateMapper.class);
        RmqAlertState row = new RmqAlertState();
        row.setId(1L);
        row.setRuleId(4L);
        row.setFingerprint("fingerprint");
        row.setStatus("FIRED");
        row.setConsecutiveHits(2);
        when(mapper.selectOne(any())).thenReturn(row);
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper);

        assertThat(repository.find(new AlertStateKey(4L, "fingerprint")))
                .hasValueSatisfying(state -> {
                    assertThat(state.status()).isEqualTo(AlertStateStatus.OK);
                    assertThat(state.consecutiveHits()).isEqualTo(2);
                });
    }

    @Test
    void runtimeListingToleratesUnknownStoredStatusValuesTest() {
        RmqAlertStateMapper mapper = mock(RmqAlertStateMapper.class);
        RmqAlertState unknown = new RmqAlertState();
        unknown.setId(1L);
        unknown.setRuleId(4L);
        unknown.setFingerprint("unknown");
        unknown.setStatus("FIRED");
        unknown.setConsecutiveHits(2);
        RmqAlertState lowerCase = new RmqAlertState();
        lowerCase.setId(2L);
        lowerCase.setRuleId(4L);
        lowerCase.setFingerprint("lowercase");
        lowerCase.setStatus(" firing ");
        lowerCase.setConsecutiveHits(1);
        when(mapper.selectList(any())).thenReturn(List.of(unknown, lowerCase));
        MybatisPlusAlertStateRepository repository = new MybatisPlusAlertStateRepository(mapper);
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).name("Lag").reminderInterval("30m").build();

        List<AlertRuleRuntimeVO> runtime = repository.findRuntimeByRuleIds(List.of(rule));

        assertThat(runtime)
                .extracting(AlertRuleRuntimeVO::getFingerprint, AlertRuleRuntimeVO::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("unknown", AlertStateStatus.OK),
                        org.assertj.core.groups.Tuple.tuple("lowercase", AlertStateStatus.FIRING));
    }
}
