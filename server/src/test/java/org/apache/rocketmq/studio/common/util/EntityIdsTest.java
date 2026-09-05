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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityIdsTest {

    @Test
    void parsesTrimmedPositiveIdentifiers() {
        assertThat(EntityIds.parseId(" 42 ")).isEqualTo(42L);
    }

    @Test
    void rejectsZeroAndNegativeIdentifiers() {
        assertThatThrownBy(() -> EntityIds.parseId("0"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(400))
                .hasMessageContaining("positive numeric value");
        assertThatThrownBy(() -> EntityIds.parseId("-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("positive numeric value");
    }

    @Test
    void rejectsBlankAndNullIdentifiersTest() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> EntityIds.parseId(blank))
                    .as("blank id %s", blank)
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getCode()).isEqualTo(400))
                    .hasMessageContaining("id is required");
        }
    }

    @Test
    void rejectsNonNumericIdentifiersTest() {
        for (String invalid : new String[] {"abc", "42.0", "42abc", "0x2A",
            "99999999999999999999"}) {
            assertThatThrownBy(() -> EntityIds.parseId(invalid))
                    .as("invalid id %s", invalid)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("numeric value")
                    .hasMessageContaining(invalid);
        }
    }

    @Test
    void acceptsSignedAndZeroPaddedIdentifiersTest() {
        assertThat(EntityIds.parseId("+7")).isEqualTo(7L);
        assertThat(EntityIds.parseId(" 007 ")).isEqualTo(7L);
        assertThat(EntityIds.parseId("2147483648")).isEqualTo(2147483648L);
    }
}
