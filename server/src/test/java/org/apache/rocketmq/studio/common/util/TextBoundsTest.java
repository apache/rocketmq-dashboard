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

class TextBoundsTest {

    /** Emoji is one code point, two UTF-16 chars: the case every bound in TextBounds has to survive. */
    private static final String EMOJI = "\uD83D\uDE00";

    @Test
    void codePointCountShouldCountASupplementaryCharacterOnceTest() {
        assertThat(TextBounds.codePointCount(EMOJI)).isEqualTo(1);
        assertThat(TextBounds.codePointCount("a" + EMOJI + "b")).isEqualTo(3);
        assertThat(TextBounds.codePointCount("")).isZero();
        assertThat(TextBounds.codePointCount(null)).isZero();
    }

    @Test
    void truncateShouldNotSplitASurrogatePairTest() {
        // Four code points cut to three: the emoji is the third, so a char-based cut would stop on
        // its high surrogate instead.
        String value = "ab" + EMOJI + "d";

        String truncated = TextBounds.truncate(value, 3);

        assertThat(truncated).isEqualTo("ab" + EMOJI);
        assertThat(truncated.codePointCount(0, truncated.length())).isEqualTo(3);
    }

    @Test
    void truncateShouldKeepAValueThatFitsTest() {
        String atCap = "a".repeat(3) + EMOJI;

        // Four code points, five chars: a char-based cap would cut one code point short here.
        assertThat(TextBounds.truncate(atCap, 4)).isSameAs(atCap);
        assertThat(TextBounds.truncate("short", 500)).isEqualTo("short");
        assertThat(TextBounds.truncate(null, 500)).isNull();
    }

    @Test
    void truncateShouldAppendTheSuffixOnlyToACutValueTest() {
        String value = "x".repeat(4) + EMOJI;

        assertThat(TextBounds.truncate(value, 4, "...")).isEqualTo("x".repeat(4) + "...");
        // The value ends with the same characters as the suffix and is still not touched: only a cut
        // may add the marker, or a value that fits would claim it was abbreviated.
        assertThat(TextBounds.truncate("ab...", 500, "...")).isEqualTo("ab...");
        assertThat(TextBounds.truncate(null, 500, "...")).isNull();
    }

    @Test
    void truncateShouldTreatAnEmptyBudgetAsNoTextTest() {
        assertThat(TextBounds.truncate("ab", 0)).isEmpty();
        assertThat(TextBounds.truncate("ab", -1)).isEmpty();
        assertThat(TextBounds.truncate("ab", -1, "...")).isEqualTo("...");
        // An empty value already fits any budget, so no suffix is invented for it.
        assertThat(TextBounds.truncate("", 0, "...")).isEmpty();
    }
}
