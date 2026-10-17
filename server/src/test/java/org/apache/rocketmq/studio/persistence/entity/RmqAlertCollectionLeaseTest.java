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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the lease entity {@link RmqAlertCollectionLease}: it maps to the
 * {@code rmq_alert_collection_lease} table and every column round-trips.
 */
class RmqAlertCollectionLeaseTest {

    @Test
    void theEntityMapsToTheLeaseTable() {
        TableName annotation = RmqAlertCollectionLease.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_alert_collection_lease");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAlertCollectionLease lease = new RmqAlertCollectionLease();
        lease.setId(7L);
        lease.setLeaseName("alert-collection");
        lease.setHolderId("replica-1");
        lease.setExpiresAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        lease.setGmtModified(LocalDateTime.of(2026, 1, 2, 3, 5));

        assertThat(lease.getId()).isEqualTo(7L);
        assertThat(lease.getLeaseName()).isEqualTo("alert-collection");
        assertThat(lease.getHolderId()).isEqualTo("replica-1");
        assertThat(lease.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(lease.getGmtModified()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 5));
    }
}
