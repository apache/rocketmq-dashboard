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

import org.springframework.util.StringUtils;

/**
 * Escapes user-supplied search terms before they are handed to a SQL {@code LIKE} pattern.
 *
 * <p>MyBatis-Plus wraps the value of {@code like(column, value)} as {@code %value%} and appends no
 * {@code ESCAPE} clause, so {@code %}, {@code _} and {@code \} keep their pattern meaning. A search
 * for a literal {@code %} therefore matches every row, and {@code _} matches every non-empty value,
 * which is the opposite of what an operator typing those characters is asking for. Prefixing the
 * three characters with a backslash makes them literal on both databases this project runs on:
 * MySQL, and H2 in {@code MODE=MySQL} (the dev profile), where the backslash is the default
 * {@code LIKE} escape character.
 *
 * <p>Apply this at the boundary where the request value first becomes a query condition, once per
 * value, and never to an already-escaped value.
 */
public final class SqlLikeUtils {

    /** Default {@code LIKE} escape character on MySQL and on H2 in {@code MODE=MySQL}. */
    private static final char ESCAPE_CHARACTER = '\\';

    private SqlLikeUtils() {
    }

    /**
     * Escapes the {@code LIKE} wildcards and the escape character itself so {@code value} matches
     * literally. {@code null} and blank values are returned unchanged; callers routinely pass a
     * {@code null} that also disables the condition.
     *
     * @param value the raw search term, typically straight from a request parameter
     * @return the term with {@code \}, {@code %} and {@code _} escaped, or the input when blank
     */
    public static String escape(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == ESCAPE_CHARACTER || current == '%' || current == '_') {
                escaped.append(ESCAPE_CHARACTER);
            }
            escaped.append(current);
        }
        return escaped.toString();
    }
}
