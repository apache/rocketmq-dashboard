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
 * Pins the Studio user entity {@link RmqStudioUser}: it maps to the {@code rmq_studio_user}
 * table and every column round-trips - the login identity with its (boxed) admin and enabled
 * flags and the password-changed timestamp the session-overview page shows.
 */
class RmqStudioUserTest {

    @Test
    void theEntityMapsToTheUserTable() {
        TableName annotation = RmqStudioUser.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_studio_user");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setPasswordHash("$2a$10$hash");
        user.setAdmin(true);
        user.setEnabled(false);
        user.setPasswordChangedAt(LocalDateTime.of(2026, 1, 2, 3, 4));

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(user.getAdmin()).isTrue();
        assertThat(user.getEnabled()).isFalse();
        assertThat(user.getPasswordChangedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
    }

    @Test
    void theFlagsAreBoxedSoNullMeansUnknown() {
        RmqStudioUser user = new RmqStudioUser();
        assertThat(user.getAdmin()).isNull();
        assertThat(user.getEnabled()).isNull();
    }
}
