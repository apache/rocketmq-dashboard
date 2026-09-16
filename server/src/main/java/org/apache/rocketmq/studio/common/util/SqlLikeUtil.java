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
 * Shared SQL LIKE helpers for literal user search terms.
 * Callers must pair {@link #escape(String)} with an explicit {@code ESCAPE}
 * clause so the pattern also works on databases (H2) that do not treat
 * backslash as the default LIKE escape character.
 */
public final class SqlLikeUtil {

    /** SQL fragment appended after a LIKE predicate. */
    public static final String ESCAPE_CLAUSE = " ESCAPE '\\'";

    private SqlLikeUtil() {
    }

    /**
     * Escapes {@code \}, {@code %} and {@code _} so the input matches literally.
     */
    public static String escape(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Escapes the input and wraps it as a {@code %value%} contains-pattern.
     */
    public static String containsPattern(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw == null ? null : "%" + raw + "%";
        }
        return "%" + escape(raw) + "%";
    }

    /**
     * Builds a MyBatis-Plus {@code apply} fragment for a literal contains search,
     * including the explicit escape clause.
     */
    public static String containsSql(String column) {
        return column + " LIKE {0}" + ESCAPE_CLAUSE;
    }
}
