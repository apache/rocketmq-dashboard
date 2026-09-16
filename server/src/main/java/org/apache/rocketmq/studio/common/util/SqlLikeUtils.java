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
 * Escapes user-supplied search terms so they reach SQL {@code LIKE} as literal text, and names the
 * escape character in the generated predicate instead of leaving it to the engine's default.
 *
 * <p>MyBatis-Plus wraps the value of {@code like(column, value)} as {@code %value%} and appends no
 * {@code ESCAPE} clause, so the caller's own {@code %}, {@code _} and {@code \} keep their pattern
 * meaning: {@code search=%} matches every row, {@code search=_} matches every non-empty value, and
 * a full-table match can push the capped audit export over its limit.
 *
 * <p>Use {@link #likePredicate(String)} rather than {@code like(...)}, because it is the only form
 * that carries the escape character into the SQL. Naming the character matters: the two quoted
 * spellings of the backslash do not both work, as measured against the two engines this project
 * runs on (H2 2.4.240, which the test suite uses, and MySQL 8.0.46, which it does not):
 *
 * <pre>
 *   ESCAPE '\'         H2: accepted                 MySQL: syntax error near ''\'' at line 1
 *   ESCAPE '\\'        H2: Error in LIKE ESCAPE     MySQL: accepted
 *   ESCAPE CHAR(92)    H2: accepted                 MySQL: accepted
 * </pre>
 *
 * <p>{@code CHAR(92)} spells the backslash by code point, so it is the one form that means the same
 * character on both engines and is unaffected by the string-literal rules a client applies before
 * the statement reaches the server. It also stays correct when MySQL runs with
 * {@code NO_BACKSLASH_ESCAPES}, where the backslash is not the assumed {@code LIKE} escape either.
 */
public final class SqlLikeUtils {

    /** The escape character the generated predicate names. */
    private static final char ESCAPE_CHARACTER = '\\';

    /** Code point of {@link #ESCAPE_CHARACTER}, for the {@code ESCAPE} clause. */
    private static final int ESCAPE_CODE_POINT = 92;

    /**
     * The explicit {@code ESCAPE} clause that every {@code LIKE} predicate built here carries. Kept
     * public so tests assert the clause the code actually emits, including the raw-SQL checks that
     * run it against a database.
     */
    public static final String LIKE_ESCAPE_CLAUSE = " ESCAPE CHAR(" + ESCAPE_CODE_POINT + ")";

    private SqlLikeUtils() {
    }

    /**
     * Builds a {@code LIKE} predicate for {@code column} whose escape character is explicit. The
     * returned text binds one value, so pass the term through {@link #contains(String)}:
     * {@code apply(SqlLikeUtils.likePredicate("name"), SqlLikeUtils.contains(search))}.
     *
     * @param column the column to match, without quotes or a table alias
     * @return the predicate, for example {@code name LIKE {0} ESCAPE CHAR(92)}
     */
    public static String likePredicate(String column) {
        return column + " LIKE {0}" + LIKE_ESCAPE_CLAUSE;
    }

    /**
     * Wraps {@code value} as a contains pattern, escaping it first, so the term matches as literal
     * text anywhere in the column. Returns {@code null} for a blank value so the caller's condition
     * and the bound value stay in step; a non-blank value never produces {@code null}.
     *
     * @param value the raw search term, typically straight from a request parameter
     * @return {@code %escaped%}, or {@code null} when the value is blank
     */
    public static String contains(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "%" + escape(value) + "%";
    }

    /**
     * Escapes {@link #ESCAPE_CHARACTER} along with {@code %} and {@code _} so the result matches
     * literally. {@code null} and blank values are returned unchanged; callers routinely pass a
     * {@code null} that also disables the condition.
     *
     * @param value the raw search term
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
