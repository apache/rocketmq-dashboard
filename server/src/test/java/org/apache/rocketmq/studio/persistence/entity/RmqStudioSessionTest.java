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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RmqStudioSession}: the persisted session row must not expose its
 * token hash through {@code toString} while still comparing equal on it.
 */
class RmqStudioSessionTest {

    @Test
    void toStringOmitsTheTokenHash() {
        RmqStudioSession session = new RmqStudioSession();
        session.setId(1L);
        session.setUserId(9L);
        session.setTokenHash("sha256-of-session-token");

        String value = session.toString();

        assertThat(value).contains("userId=9");
        assertThat(value).doesNotContain("tokenHash").doesNotContain("sha256-of-session-token");
    }

    @Test
    void dataEqualityCoversTheTokenHash() {
        RmqStudioSession first = new RmqStudioSession();
        first.setId(1L);
        first.setUserId(9L);
        first.setTokenHash("hash-a");

        RmqStudioSession same = new RmqStudioSession();
        same.setId(1L);
        same.setUserId(9L);
        same.setTokenHash("hash-a");

        RmqStudioSession rotated = new RmqStudioSession();
        rotated.setId(1L);
        rotated.setUserId(9L);
        rotated.setTokenHash("hash-b");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(rotated);
    }
}
