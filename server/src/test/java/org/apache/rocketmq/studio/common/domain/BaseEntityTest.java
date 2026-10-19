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
package org.apache.rocketmq.studio.common.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BaseEntity}: the id and audit timestamps every persisted entity inherits round-trip
 * through the setters, and a fresh entity carries no id - persistence assigns it.
 */
class BaseEntityTest {

    private static class SampleEntity extends BaseEntity {
    }

    @Test
    void theInheritedFieldsRoundTrip() {
        SampleEntity entity = new SampleEntity();
        entity.setId(42L);
        entity.setGmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4));
        entity.setGmtModified(LocalDateTime.of(2026, 1, 3, 4, 5));

        assertThat(entity.getId()).isEqualTo(42L);
        assertThat(entity.getGmtCreate()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(entity.getGmtModified()).isEqualTo(LocalDateTime.of(2026, 1, 3, 4, 5));
    }

    @Test
    void aFreshEntityCarriesNoId() {
        assertThat(new SampleEntity().getId()).isNull();
    }
}
