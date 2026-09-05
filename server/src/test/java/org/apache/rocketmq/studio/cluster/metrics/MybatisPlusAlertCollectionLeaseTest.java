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
package org.apache.rocketmq.studio.cluster.metrics;

import org.apache.rocketmq.studio.persistence.entity.RmqAlertCollectionLease;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertCollectionLeaseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlusAlertCollectionLeaseTest {

    @Test
    void renewsAnUnexpiredLeaseHeldByThisReplicaTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.renew(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.renew()).isTrue();
        verify(mapper).renew(eq("native-alert-collection"), anyString(), any(), any());
    }

    @Test
    void reportsLeaseLossWhenTheDatabaseNoLongerMatchesThisHolderTest() {
        AlertingProperties properties = new AlertingProperties();
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.renew(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(0);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.renew()).isFalse();
        verify(mapper).renew(eq("native-alert-collection"), anyString(), any(), any());
    }

    @Test
    void tryAcquireRenewsTheLiveRowWhenPossible() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isTrue();
        verify(mapper, never()).insert(any(RmqAlertCollectionLease.class));
    }

    @Test
    void tryAcquireInsertsItsOwnLeaseWhenNoLiveRowCanBeRenewed() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(0);
        when(mapper.insert(any(RmqAlertCollectionLease.class))).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isTrue();
        ArgumentCaptor<String> holder = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RmqAlertCollectionLease> inserted = ArgumentCaptor.forClass(RmqAlertCollectionLease.class);
        verify(mapper).acquire(eq("native-alert-collection"), holder.capture(), any(), any());
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getHolderId()).isEqualTo(holder.getValue());
        assertThat(inserted.getValue().getLeaseName()).isEqualTo("native-alert-collection");
    }

    @Test
    void tryAcquireLosesTheRaceWhenAnotherReplicaInsertsFirst() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(0);
        when(mapper.insert(any(RmqAlertCollectionLease.class)))
                .thenThrow(new DuplicateKeyException("duplicate lease"));

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isFalse();
    }

    @Test
    void renewFallsBackToOneMinuteForMalformedDurations() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("garbage");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.renew(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.renew()).isTrue();
        ArgumentCaptor<LocalDateTime> renewedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).renew(eq("native-alert-collection"), anyString(),
                renewedAt.capture(), expiresAt.capture());
        assertThat(Duration.between(renewedAt.getValue(), expiresAt.getValue()))
                .isEqualTo(Duration.ofMinutes(1));
    }
}
