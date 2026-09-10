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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Aliyun converter base64 helper: message bodies arrive base64-encoded
 * and are decoded strictly as UTF-8, degrading to null for blank, malformed, or
 * non-UTF-8 payloads.
 */
class AliyunConvertersPayloadTest {

    @Test
    void decodesValidBase64PayloadsAsUtf8() {
        String ascii = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        String chinese = Base64.getEncoder().encodeToString("\u4e2d\u6587".getBytes(StandardCharsets.UTF_8));

        assertThat(AliyunConverters.tryBase64Decode(ascii)).isEqualTo("hello");
        assertThat(AliyunConverters.tryBase64Decode(chinese)).isEqualTo("\u4e2d\u6587");
    }

    @Test
    void returnsNullForBlankAndMalformedPayloads() {
        assertThat(AliyunConverters.tryBase64Decode(null)).isNull();
        assertThat(AliyunConverters.tryBase64Decode("  ")).isNull();
        assertThat(AliyunConverters.tryBase64Decode("!!!not-base64!!!")).isNull();
    }

    @Test
    void returnsNullForPayloadsThatAreNotValidUtf8() {
        byte[] invalidUtf8 = {(byte) 0xff, (byte) 0xfe};
        String base64 = Base64.getEncoder().encodeToString(invalidUtf8);

        assertThat(AliyunConverters.tryBase64Decode(base64)).isNull();
    }
}
