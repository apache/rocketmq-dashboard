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

import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesResponseBody;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@code bodyEncoding} vocabulary of the Aliyun message converter to the one the rest of
 * Studio publishes: {@code UTF-8} for text, {@code BASE64} for a payload that is not text, and
 * {@code null} when the API returned no body at all.
 */
class AliyunConvertersBodyEncodingTest {

    private static MessageRecordVO recordWithBody(String body) {
        return AliyunConverters.toMessageRecord(ListMessagesResponseBody.List.builder()
                .messageId("msg-1")
                .body(body)
                .build());
    }

    @Test
    void shouldReportUtf8WhenTheBase64BodyDecodesToText() {
        String encoded = Base64.getEncoder()
                .encodeToString("{\"orderId\":42}".getBytes(StandardCharsets.UTF_8));

        MessageRecordVO record = recordWithBody(encoded);

        assertThat(record.getBody()).isEqualTo("{\"orderId\":42}");
        assertThat(record.getBodyEncoding()).isEqualTo("UTF-8");
        // ListMessages without BodySize reports the unknown sentinel, not a fabricated zero.
        assertThat(record.getSize()).isEqualTo(MessageRecordVO.UNKNOWN_SIZE);
    }

    @Test
    void shouldReportBase64WhenThePayloadIsBinary() {
        // 0xFF 0xFE is not valid UTF-8, so the decoder reports a coding error and the Base64
        // the OpenAPI returned is the only faithful way to show the payload.
        String encoded = Base64.getEncoder().encodeToString(new byte[] {(byte) 0xFF, (byte) 0xFE});

        MessageRecordVO record = recordWithBody(encoded);

        assertThat(record.getBody()).isEqualTo(encoded);
        assertThat(record.getBodyEncoding()).isEqualTo("BASE64");
    }

    @Test
    void shouldKeepLiteralTextWhenTheBodyIsNotBase64() {
        MessageRecordVO record = recordWithBody("{\"orderId\":42}");

        assertThat(record.getBody()).isEqualTo("{\"orderId\":42}");
        assertThat(record.getBodyEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void shouldReportNoEncodingWhenTheApiReturnedNoBody() {
        assertThat(recordWithBody(null).getBodyEncoding()).isNull();
        assertThat(recordWithBody(null).getBody()).isNull();
        assertThat(recordWithBody("  ").getBodyEncoding()).isNull();
    }

    @Test
    void shouldNeverEmitAnEncodingOutsideThePublishedVocabulary() {
        String text = Base64.getEncoder().encodeToString("plain".getBytes(StandardCharsets.UTF_8));
        String binary = Base64.getEncoder().encodeToString(new byte[] {(byte) 0xFF});

        for (String body : Arrays.asList(text, binary, "not base64 at all!!", "", null)) {
            assertThat(recordWithBody(body).getBodyEncoding()).isIn("UTF-8", "BASE64", null);
        }
    }
}