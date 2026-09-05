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
package org.apache.rocketmq.studio.provider.alibaba;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Aliyun converter time helpers: epoch-millis parsing/formatting happens
 * in the Aliyun console time zone (Asia/Shanghai) with second precision.
 */
class AliyunConvertersTimeTest {

    @Test
    void parsesKnownAliyunLocalTimesIntoEpochMillis() {
        long expected = LocalDateTime.of(2026, 7, 1, 10, 0, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        assertThat(AliyunConverters.parseTimeMillis("2026-07-01 10:00:00")).isEqualTo(expected);
        assertThat(AliyunConverters.parseTimeMillis("2026-07-01 10:00:00"))
                .isEqualTo(expected);
    }

    @Test
    void formatsEpochMillisBackIntoAliyunLocalTime() {
        long epoch = LocalDateTime.of(2026, 7, 1, 10, 0, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        assertThat(AliyunConverters.formatTimeMillis(epoch)).isEqualTo("2026-07-01 10:00:00");
        assertThat(AliyunConverters.formatTimeMillis(0L)).isEqualTo("1970-01-01 08:00:00");
    }

    @Test
    void roundTripsWholeSecondsThroughTheAliyunTimeZone() {
        long millis = 1_780_000_000_000L;

        assertThat(AliyunConverters.parseTimeMillis(AliyunConverters.formatTimeMillis(millis)))
                .isEqualTo(millis);
    }

    @Test
    void toleratesBlankAndMalformedTimeInputs() {
        assertThat(AliyunConverters.parseTimeMillis(null)).isZero();
        assertThat(AliyunConverters.parseTimeMillis("  ")).isZero();
        assertThat(AliyunConverters.parseTimeMillis("not-a-time")).isZero();
        assertThat(AliyunConverters.parseDateTime(null)).isNull();
        assertThat(AliyunConverters.parseDateTime("not-a-time")).isNull();
    }
}
