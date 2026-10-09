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

class LikePatternsTest {

    @Test
    void escapesEveryLikeMetacharacterTest() {
        assertThat(LikePatterns.escape("topic_a")).isEqualTo("topic\\_a");
        assertThat(LikePatterns.escape("100%")).isEqualTo("100\\%");
        assertThat(LikePatterns.escape("%DLQ%group")).isEqualTo("\\%DLQ\\%group");
    }

    @Test
    void escapesTheEscapeCharacterItselfTest() {
        // A term that already contains a backslash must not be able to escape the next character
        // of the pattern it lands in.
        assertThat(LikePatterns.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(LikePatterns.escape("a\\_b")).isEqualTo("a\\\\\\_b");
    }

    @Test
    void leavesPlainTextAloneTest() {
        assertThat(LikePatterns.escape("orders")).isEqualTo("orders");
        assertThat(LikePatterns.escape("GID-prod-1")).isEqualTo("GID-prod-1");
    }

    @Test
    void passesAbsentFiltersThroughTest() {
        assertThat(LikePatterns.escape(null)).isNull();
        assertThat(LikePatterns.escape("")).isEmpty();
        assertThat(LikePatterns.escape("   ")).isEqualTo("   ");
    }
}
