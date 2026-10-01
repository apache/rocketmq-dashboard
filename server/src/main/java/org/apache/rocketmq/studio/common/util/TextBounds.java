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

/**
 * Bounds the user-facing text this codebase abbreviates and validates.
 *
 * <p>Every cap here is a budget of characters, and a UTF-16 {@code char} is the wrong unit for it:
 * a supplementary character (an emoji, a CJK extension character) is two chars, so a char-based cut
 * can land between its high and its low surrogate and keep half of one. A lone surrogate is not a
 * code point, has no UTF-8 encoding and is rejected by MySQL utf8mb4, so the half character reaches
 * the browser as a replacement character instead of the character it was cut out of. Counting and
 * cutting in code points keeps such a character whole or drops it whole.
 *
 * <p>A code-point budget is also what a database column width means: MySQL counts a {@code varchar}
 * in characters, not in UTF-16 chars, so 512 emoji fit a {@code varchar(512)}.
 */
public final class TextBounds {

    private TextBounds() {
    }

    /** Number of code points in {@code value}, 0 for null: the unit every cap is counted in. */
    public static int codePointCount(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /**
     * Longest prefix of {@code value} that fits in {@code maxCodePoints} code points, or {@code value}
     * itself when it already fits. The cut always lands on a code point boundary, so a supplementary
     * character is never split into a lone surrogate.
     */
    public static String truncate(String value, int maxCodePoints) {
        int budget = Math.max(0, maxCodePoints);
        if (codePointCount(value) <= budget) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, budget));
    }

    /**
     * {@code value} when it fits in {@code maxCodePoints} code points, otherwise its
     * {@link #truncate(String, int) truncated} prefix with {@code suffix} appended. The suffix marks a
     * value that was cut, so it is never appended to one that merely ends the same way.
     */
    public static String truncate(String value, int maxCodePoints, String suffix) {
        if (codePointCount(value) <= Math.max(0, maxCodePoints)) {
            return value;
        }
        return truncate(value, maxCodePoints) + suffix;
    }
}
