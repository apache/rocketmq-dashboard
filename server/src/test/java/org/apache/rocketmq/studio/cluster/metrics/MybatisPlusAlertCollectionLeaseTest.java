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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void tryAcquireWinsWhenTheDatabaseInsertIsTheFirstHolderTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isTrue();
        verify(mapper).acquire(eq("native-alert-collection"), anyString(), any(), any());
    }

    @Test
    void tryAcquireInsertsWhenTheConditionalAcquireMissesTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(0);
        when(mapper.insert(any(RmqAlertCollectionLease.class))).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isTrue();
        org.mockito.ArgumentCaptor<RmqAlertCollectionLease> captor =
                org.mockito.ArgumentCaptor.forClass(RmqAlertCollectionLease.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getLeaseName()).isEqualTo("native-alert-collection");
        assertThat(captor.getValue().getHolderId()).isNotBlank();
    }

    @Test
    void tryAcquireReportsLossWhenTheInsertLosesARaceTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT30S");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(0);
        when(mapper.insert(any(RmqAlertCollectionLease.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("race lost"));

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isFalse();
    }

    @Test
    void malformedLeaseDurationFallsBackToOneMinuteTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("not-a-duration");
        RmqAlertCollectionLeaseMapper mapper = mock(RmqAlertCollectionLeaseMapper.class);
        when(mapper.acquire(eq("native-alert-collection"), anyString(), any(), any())).thenReturn(0);
        when(mapper.insert(any(RmqAlertCollectionLease.class))).thenReturn(1);

        MybatisPlusAlertCollectionLease lease = new MybatisPlusAlertCollectionLease(properties, mapper);

        assertThat(lease.tryAcquire()).isTrue();
        org.mockito.ArgumentCaptor<RmqAlertCollectionLease> captor =
                org.mockito.ArgumentCaptor.forClass(RmqAlertCollectionLease.class);
        verify(mapper).insert(captor.capture());
        java.time.LocalDateTime expiresAt = captor.getValue().getExpiresAt();
        java.time.LocalDateTime lowerBound = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .plusSeconds(58);
        java.time.LocalDateTime upperBound = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                .plusSeconds(62);
        assertThat(expiresAt).isBetween(lowerBound, upperBound);
    }
}
