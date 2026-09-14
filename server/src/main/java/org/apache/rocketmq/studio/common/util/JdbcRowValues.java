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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Reads computed / aggregate columns out of the {@code Map<String, Object>} rows returned by
 * MyBatis-Plus {@code selectMaps}.
 *
 * <p>Every lookup matches the requested label case-insensitively and ignores underscores,
 * because JDBC drivers are free to return result-set label casing differently (MySQL echoes the
 * alias exactly as written, other drivers upper-case it) and MyBatis may or may not translate a
 * {@code snake_case} label into a {@code camelCase} map key depending on configuration. Callers
 * therefore never have to hard-code a pair of candidate keys.</p>
 */
public final class JdbcRowValues {

    private JdbcRowValues() {
    }

    /** Returns the value behind {@code key}, or {@code null} when no non-null value matches. */
    public static Object value(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty() || key == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        String normalizedKey = normalize(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() != null && normalizedKey.equals(normalize(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns the value behind {@code key} as text, or {@code ""} when it is absent or null. */
    public static String stringValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : value.toString();
    }

    /**
     * Returns the value behind {@code key} as a {@link Long}, or {@code null} when it is absent,
     * null or blank. Numeric text is parsed so drivers that stringify aggregates still work.
     */
    public static Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    /** Returns the value behind {@code key} as a {@code long}, defaulting to zero. */
    public static long longValueOrZero(Map<String, Object> row, String key) {
        Long value = longValue(row, key);
        return value == null ? 0L : value;
    }

    /** Returns the value behind {@code key} as an {@code int}, defaulting to zero. */
    public static int intValueOrZero(Map<String, Object> row, String key) {
        Long value = longValue(row, key);
        return value == null ? 0 : value.intValue();
    }

    /** Returns the value behind {@code key} as a {@link LocalDateTime}, or {@code null}. */
    public static LocalDateTime dateTimeValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDateTime.parse(text.trim());
        }
        return null;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.replace("_", "").toLowerCase(Locale.ROOT);
    }
}
