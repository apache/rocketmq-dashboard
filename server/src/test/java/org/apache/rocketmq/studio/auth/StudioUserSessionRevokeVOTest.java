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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the revoke view of {@link StudioUserSessionRevokeVO}: the builder round-trips the user and
 * the revoked-session count, and a fresh view counts zero revocations.
 */
class StudioUserSessionRevokeVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        StudioUserSessionRevokeVO vo = StudioUserSessionRevokeVO.builder()
                .userId(7L)
                .revokedSessionCount(3)
                .build();

        assertThat(vo.getUserId()).isEqualTo(7L);
        assertThat(vo.getRevokedSessionCount()).isEqualTo(3);
    }

    @Test
    void aFreshViewCountsZeroRevocations() {
        StudioUserSessionRevokeVO vo = StudioUserSessionRevokeVO.builder().build();
        assertThat(vo.getRevokedSessionCount()).isZero();
        assertThat(vo.getUserId()).isNull();
    }
}
