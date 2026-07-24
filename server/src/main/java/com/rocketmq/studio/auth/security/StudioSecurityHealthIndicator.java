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
package com.rocketmq.studio.auth.security;

import java.util.Objects;

import com.rocketmq.studio.auth.security.StudioUserRegistry.Snapshot;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class StudioSecurityHealthIndicator implements HealthIndicator {
    private static final String USER_REGISTRY = "userRegistry";
    private static final String AVAILABLE = "available";
    private static final String UNAVAILABLE = "unavailable";

    private final StudioUserRegistry registry;

    public StudioSecurityHealthIndicator(StudioUserRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public Health health() {
        try {
            Snapshot snapshot = registry.snapshot();
            if (snapshot != null && snapshot.available()) {
                return Health.up()
                    .withDetail(USER_REGISTRY, AVAILABLE)
                    .build();
            }
        } catch (RuntimeException exception) {
            // Readiness fails closed without disclosing registry failure details.
        }
        return Health.outOfService()
            .withDetail(USER_REGISTRY, UNAVAILABLE)
            .build();
    }
}
