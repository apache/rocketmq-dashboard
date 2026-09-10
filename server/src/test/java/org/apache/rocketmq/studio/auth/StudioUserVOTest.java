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
package org.apache.rocketmq.studio.auth;

import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudioUserVOTest {


    @Test
    void fromShouldMapTheEntityFields() {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(7L);
        user.setUsername("studio-admin");
        user.setAdmin(true);
        user.setEnabled(true);
        LocalDateTime changed = LocalDateTime.of(2026, 8, 1, 12, 0);
        user.setPasswordChangedAt(changed);

        StudioUserVO vo = StudioUserVO.from(user);

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getUsername()).isEqualTo("studio-admin");
        assertThat(vo.isAdmin()).isTrue();
        assertThat(vo.isEnabled()).isTrue();
        assertThat(vo.getPasswordChangedAt()).isEqualTo(changed);
    }

    @Test
    void fromShouldTreatNullBooleanFlagsAsFalse() {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(1L);
        user.setUsername("ops");

        StudioUserVO vo = StudioUserVO.from(user);

        assertThat(vo.isAdmin()).isFalse();
        assertThat(vo.isEnabled()).isFalse();
    }
}
