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
 * Unit tests for {@link InstanceIds}, the helpers crossing the numeric/string boundary for
 * instance identifiers.
 */
class InstanceIdsTest {

    @Test
    void parseLongOrNullHandlesBlankAndMissingValues() {
        assertThat(InstanceIds.parseLongOrNull(null)).isNull();
        assertThat(InstanceIds.parseLongOrNull("")).isNull();
        assertThat(InstanceIds.parseLongOrNull("   ")).isNull();
    }

    @Test
    void parseLongOrNullParsesTrimmedNumericValues() {
        assertThat(InstanceIds.parseLongOrNull("42")).isEqualTo(42L);
        assertThat(InstanceIds.parseLongOrNull(" 42 ")).isEqualTo(42L);
        assertThat(InstanceIds.parseLongOrNull("2147483648")).isEqualTo(2147483648L);
    }

    @Test
    void parseLongOrNullDegradesNonNumericValuesToNull() {
        assertThat(InstanceIds.parseLongOrNull("abc")).isNull();
        assertThat(InstanceIds.parseLongOrNull("42.0")).isNull();
        assertThat(InstanceIds.parseLongOrNull("99999999999999999999")).isNull();
    }

    @Test
    void asStringRoundTripsWithoutNullArtifacts() {
        assertThat(InstanceIds.asString(null)).isNull();
        assertThat(InstanceIds.asString(42L)).isEqualTo("42");
        assertThat(InstanceIds.asString(2147483648L)).isEqualTo("2147483648");
    }
}
