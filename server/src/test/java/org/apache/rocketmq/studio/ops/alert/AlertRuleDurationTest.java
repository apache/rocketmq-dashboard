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
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleDurationTest {
    @Test
    void parsesTheRuleDurationSyntaxTest() {
        assertThat(AlertRuleDuration.parse("1h30m")).isEqualTo(Duration.ofMinutes(90));
        assertThat(AlertRuleDuration.parse(null)).isEqualTo(Duration.ZERO);
    }

    @Test
    void rejectsDurationsThatOverflowTest() {
        assertThatThrownBy(() -> AlertRuleDuration.parse("9223372036854775807y"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> AlertRuleDuration.parse("9223372036854775807s9223372036854775807s"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
    }
}
