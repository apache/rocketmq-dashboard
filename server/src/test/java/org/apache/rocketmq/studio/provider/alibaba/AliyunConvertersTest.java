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

import java.time.LocalDateTime;
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
    void parseDateTimeShouldTolerateNullBlankAndMalformedValues() {
        assertThat(AliyunConverters.parseDateTime(null)).isNull();
        assertThat(AliyunConverters.parseDateTime("  ")).isNull();
        assertThat(AliyunConverters.parseDateTime("not-a-date")).isNull();

        assertThat(AliyunConverters.parseDateTime("2026-08-21 10:30:00"))
                .isEqualTo(LocalDateTime.of(2026, 8, 21, 10, 30, 0));
    }

    @Test
    void parseTimeMillisShouldReturnZeroForMissingAndInvalidValues() {
        assertThat(AliyunConverters.parseTimeMillis(null)).isZero();
        assertThat(AliyunConverters.parseTimeMillis(" ")).isZero();
        assertThat(AliyunConverters.parseTimeMillis("garbage")).isZero();

        // Asia/Shanghai is UTC+8, so 1970-01-01 08:00 local is epoch zero.
        assertThat(AliyunConverters.parseTimeMillis("1970-01-01 08:00:00")).isZero();
    }

    @Test
    void formatTimeMillisShouldRenderInTheAliyunZone() {
        assertThat(AliyunConverters.formatTimeMillis(0L)).isEqualTo("1970-01-01 08:00:00");
        assertThat(AliyunConverters.formatTimeMillis(1_774_059_600_000L))
                .isEqualTo("2026-03-21 10:20:00");
    }

    @Test
    void tryBase64DecodeShouldDecodeUtf8AndTolerateInvalidInput() {
        assertThat(AliyunConverters.tryBase64Decode(null)).isNull();
        assertThat(AliyunConverters.tryBase64Decode("  ")).isNull();
        assertThat(AliyunConverters.tryBase64Decode("not base64!")).isNull();

        assertThat(AliyunConverters.tryBase64Decode(Base64.getEncoder()
                .encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isEqualTo("hello");
        assertThat(AliyunConverters.tryBase64Decode(Base64.getEncoder()
                .encodeToString("\u4e2d\u6587".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isEqualTo("\u4e2d\u6587");

        // Syntactically valid base64 that does not decode to UTF-8 must degrade to null.
        assertThat(AliyunConverters.tryBase64Decode(Base64.getEncoder()
                .encodeToString(new byte[]{(byte) 0xC3, 0x28}))).isNull();
    }
}
