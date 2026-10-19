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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link InstanceIds}: the helper that carries numeric instance identifiers across the
 * String API boundary. The never-throw rule is the contract - a malformed id from the wire must
 * read as "no id", not as a 500.
 */
class InstanceIdsTest {

    @Test
    void aNumericStringParsesToItsLong() {
        assertThat(InstanceIds.parseLongOrNull("42")).isEqualTo(42L);
        assertThat(InstanceIds.parseLongOrNull("0")).isZero();
        assertThat(InstanceIds.parseLongOrNull("-7")).isEqualTo(-7L);
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(InstanceIds.parseLongOrNull("  42  ")).isEqualTo(42L);
    }

    @Test
    void nullAndBlankReadAsNoId() {
        assertThat(InstanceIds.parseLongOrNull(null)).isNull();
        assertThat(InstanceIds.parseLongOrNull("")).isNull();
        assertThat(InstanceIds.parseLongOrNull("   ")).isNull();
    }

    @Test
    void aMalformedIdReadsAsNoIdNeverAThrow() {
        assertThat(InstanceIds.parseLongOrNull("instance-a")).isNull();
        assertThat(InstanceIds.parseLongOrNull("4.2")).isNull();
        assertThat(InstanceIds.parseLongOrNull("99999999999999999999")).isNull();
    }

    @Test
    void asStringRoundTripsWithoutNullLiteralPollution() {
        assertThat(InstanceIds.asString(42L)).isEqualTo("42");
        assertThat(InstanceIds.asString(null)).isNull();
    }

    @Test
    void theStringRoundTripIsLossless() {
        assertThat(InstanceIds.parseLongOrNull(InstanceIds.asString(12345L))).isEqualTo(12345L);
    }
}
