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

import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponseBody;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersTest {

    @Test
    void toInstanceOptionShouldClampCountsOutsideTheIntegerRange() {
        ListInstancesResponseBody.List data = ListInstancesResponseBody.List.builder()
                .topicCount(Long.MAX_VALUE)
                .groupCount(Long.MIN_VALUE)
                .build();

        var result = AliyunConverters.toInstanceOptionVO(data);

        assertThat(result.getTopicCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getGroupCount()).isZero();
    }

    @Test
    void parseDateTimeShouldParseShanghaiFormatAndRejectMalformed() {
        assertThat(AliyunConverters.parseDateTime("2026-08-23 10:00:00"))
                .isEqualTo(LocalDateTime.of(2026, 8, 23, 10, 0, 0));
        assertThat(AliyunConverters.parseDateTime(null)).isNull();
        assertThat(AliyunConverters.parseDateTime("  ")).isNull();
        assertThat(AliyunConverters.parseDateTime("2026/08/23 10:00:00")).isNull();
    }

    @Test
    void parseTimeMillisShouldConvertUsingShanghaiZone() {
        long expected = LocalDateTime.of(2026, 8, 23, 10, 0, 0)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli();
        assertThat(AliyunConverters.parseTimeMillis("2026-08-23 10:00:00")).isEqualTo(expected);
        assertThat(AliyunConverters.parseTimeMillis(null)).isZero();
        assertThat(AliyunConverters.parseTimeMillis("not-a-time")).isZero();
    }

    @Test
    void formatTimeMillisShouldRenderShanghaiClock() {
        long utcTwoAm = Instant.parse("2026-08-23T02:00:00Z").toEpochMilli();

        assertThat(AliyunConverters.formatTimeMillis(utcTwoAm)).isEqualTo("2026-08-23 10:00:00");
    }

    @Test
    void tryBase64DecodeShouldDecodeUtf8AndRejectOthers() {
        String encoded = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        assertThat(AliyunConverters.tryBase64Decode(encoded)).isEqualTo("hello");
        assertThat(AliyunConverters.tryBase64Decode(null)).isNull();
        assertThat(AliyunConverters.tryBase64Decode("   ")).isNull();
        assertThat(AliyunConverters.tryBase64Decode("not-base64!!!")).isNull();
        // Valid base64 whose payload is not valid UTF-8 must also degrade to null.
        String invalidUtf8 = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThat(AliyunConverters.tryBase64Decode(invalidUtf8)).isNull();
    }
}
