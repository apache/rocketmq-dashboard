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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertCollectionLease;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertCollectionLeaseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Coordinates native collection across Studio replicas through a short-lived database lease. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MybatisPlusAlertCollectionLease implements AlertCollectionLease {
    private static final String LEASE_NAME = "native-alert-collection";

    private final AlertingProperties properties;
    private final RmqAlertCollectionLeaseMapper mapper;
    private final String holderId = UUID.randomUUID().toString();

    @Override
    public boolean tryAcquire() {
        Instant now = Instant.now();
        Duration duration = parseDuration(properties.getCollectionLeaseDuration());
        LocalDateTime acquiredAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(now.plus(duration), ZoneOffset.UTC);
        if (mapper.acquire(LEASE_NAME, holderId, acquiredAt, expiresAt) > 0) {
            return true;
        }

        RmqAlertCollectionLease lease = new RmqAlertCollectionLease();
        lease.setLeaseName(LEASE_NAME);
        lease.setHolderId(holderId);
        lease.setExpiresAt(expiresAt);
        try {
            return mapper.insert(lease) > 0;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    private static Duration parseDuration(String configured) {
        try {
            Duration duration = Duration.parse(configured);
            if (!duration.isNegative() && !duration.isZero()) {
                return duration;
            }
        } catch (RuntimeException ignored) {
            // Fall through to a conservative duration so malformed configuration cannot disable coordination.
        }
        log.warn("Invalid native alert collection lease duration {}; using PT1M", configured);
        return Duration.ofMinutes(1);
    }
}
