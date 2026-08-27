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
}
