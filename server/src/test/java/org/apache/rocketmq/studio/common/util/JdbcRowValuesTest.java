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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRowValuesTest {

    @Test
    void readsCountsWhateverLabelCasingTheDriverReturnsTest() {
        assertThat(JdbcRowValues.longValueOrZero(row("result_count", 5L), "result_count")).isEqualTo(5L);
        assertThat(JdbcRowValues.longValueOrZero(row("RESULT_COUNT", 5L), "result_count")).isEqualTo(5L);
        assertThat(JdbcRowValues.longValueOrZero(row("Result_Count", 5L), "result_count")).isEqualTo(5L);
        assertThat(JdbcRowValues.stringValue(row("RESULT", "SUCCESS"), "result")).isEqualTo("SUCCESS");
    }

    @Test
    void readsCountsWhateverLabelWordSeparationTheDriverReturnsTest() {
        // MyBatis hands the map back with either the raw snake_case label or a camelCase key
        // depending on configuration; callers must not have to know which one they got.
        assertThat(JdbcRowValues.longValue(row("user_id", 7L), "user_id")).isEqualTo(7L);
        assertThat(JdbcRowValues.longValue(row("userId", 7L), "user_id")).isEqualTo(7L);
        assertThat(JdbcRowValues.intValueOrZero(row("activeSessionCount", "2"), "active_session_count"))
                .isEqualTo(2);
    }

    @Test
    void parsesNumericTextAndDecimalAggregatesTest() {
        assertThat(JdbcRowValues.longValueOrZero(row("bucket_count", "12"), "bucket_count")).isEqualTo(12L);
        assertThat(JdbcRowValues.longValueOrZero(row("bucket_count", " 12 "), "bucket_count")).isEqualTo(12L);
        assertThat(JdbcRowValues.longValueOrZero(row("bucket_count", BigDecimal.valueOf(12)), "bucket_count"))
                .isEqualTo(12L);
        assertThat(JdbcRowValues.intValueOrZero(row("bucket_count", 12), "bucket_count")).isEqualTo(12);
    }

    @Test
    void returnsNullLongForMissingBlankAndNullValuesTest() {
        assertThat(JdbcRowValues.longValue(row("other", 1L), "result_count")).isNull();
        assertThat(JdbcRowValues.longValue(row("result_count", ""), "result_count")).isNull();
        assertThat(JdbcRowValues.longValue(row("result_count", "  "), "result_count")).isNull();
        assertThat(JdbcRowValues.longValue(rowOfNulls("result_count"), "result_count")).isNull();
        assertThat(JdbcRowValues.longValue(null, "result_count")).isNull();
    }

    @Test
    void defaultsMissingCountsToZeroTest() {
        assertThat(JdbcRowValues.longValueOrZero(row("other", 1L), "result_count")).isZero();
        // SUM(...) over an empty result set comes back as SQL NULL rather than 0.
        assertThat(JdbcRowValues.longValueOrZero(rowOfNulls("stale_session_count"), "stale_session_count"))
                .isZero();
        assertThat(JdbcRowValues.intValueOrZero(row("other", 1L), "active_session_count")).isZero();
    }

    @Test
    void keepsSearchingWhenTheExactKeyHoldsANullValueTest() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("result_count", null);
        row.put("RESULT_COUNT", 4L);

        assertThat(JdbcRowValues.longValueOrZero(row, "result_count")).isEqualTo(4L);
    }

    @Test
    void returnsEmptyTextForMissingValuesTest() {
        assertThat(JdbcRowValues.stringValue(row("other", 1L), "result")).isEmpty();
        assertThat(JdbcRowValues.stringValue(rowOfNulls("result"), "result")).isEmpty();
        assertThat(JdbcRowValues.stringValue(null, "result")).isEmpty();
        assertThat(JdbcRowValues.stringValue(row("result", 12L), "result")).isEqualTo("12");
    }

    @Test
    void convertsEveryTemporalTypeADriverMayReturnTest() {
        LocalDateTime expected = LocalDateTime.parse("2026-08-13T00:05:00");

        assertThat(JdbcRowValues.dateTimeValue(row("last_seen_at", expected), "last_seen_at"))
                .isEqualTo(expected);
        assertThat(JdbcRowValues.dateTimeValue(
                row("last_seen_at", Timestamp.valueOf(expected)), "last_seen_at")).isEqualTo(expected);
        assertThat(JdbcRowValues.dateTimeValue(
                row("last_seen_at", Date.from(expected.toInstant(ZoneOffset.UTC))), "last_seen_at"))
                .isEqualTo(expected);
        assertThat(JdbcRowValues.dateTimeValue(row("last_seen_at", "2026-08-13T00:05:00"), "last_seen_at"))
                .isEqualTo(expected);
        assertThat(JdbcRowValues.dateTimeValue(row("last_seen_at", 5L), "last_seen_at")).isNull();
        assertThat(JdbcRowValues.dateTimeValue(row("other", expected), "last_seen_at")).isNull();
    }

    @Test
    void exposesTheRawValueBehindAMatchedLabelTest() {
        Object raw = new Object();

        assertThat(JdbcRowValues.value(row("detail", raw), "DETAIL")).isSameAs(raw);
        assertThat(JdbcRowValues.value(row("detail", raw), "unknown")).isNull();
        assertThat(JdbcRowValues.value(Map.of(), "detail")).isNull();
        assertThat(JdbcRowValues.value(row("detail", raw), null)).isNull();
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            row.put((String) keyValues[index], keyValues[index + 1]);
        }
        return row;
    }

    /** Builds a row whose only key maps to SQL NULL, which JDBC reports as a Java null. */
    private static Map<String, Object> rowOfNulls(String... keys) {
        Map<String, Object> row = new HashMap<>();
        for (String key : keys) {
            row.put(key, null);
        }
        return row;
    }
}
