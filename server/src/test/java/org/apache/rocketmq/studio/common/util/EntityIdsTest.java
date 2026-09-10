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
    void rejectsNullAndBlankIdentifiers() {
        assertThatThrownBy(() -> EntityIds.parseId(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(400))
                .hasMessage("id is required");
        assertThatThrownBy(() -> EntityIds.parseId("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("id is required");
    }

    @Test
    void rejectsNonNumericIdentifiers() {
        assertThatThrownBy(() -> EntityIds.parseId("abc"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(400))
                .hasMessage("id must be a numeric value: abc");
        assertThatThrownBy(() -> EntityIds.parseId("42abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("id must be a numeric value: 42abc");
    }

    @Test
    void acceptsPositiveIdentifiersAcrossTheLongRange() {
        assertThat(EntityIds.parseId(Long.toString(Long.MAX_VALUE)))
                .isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> EntityIds.parseId("99999999999999999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("numeric value");
    }
}
