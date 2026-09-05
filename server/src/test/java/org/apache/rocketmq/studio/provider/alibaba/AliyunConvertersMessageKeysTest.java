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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersMessageKeysTest {

    @Test
    void toMessageRecordShouldSkipNullAndBlankMessageKeys() {
        ListMessagesResponseBody.List data = ListMessagesResponseBody.List.builder()
                .messageKeys(Arrays.asList("key-a", null, " ", "key-b"))
                .build();

        assertThat(AliyunConverters.toMessageRecord(data).getKey()).isEqualTo("key-a key-b");
    }

    @Test
    void toMessageRecordShouldDecodeBase64Utf8Body() {
        ListMessagesResponseBody.List data = ListMessagesResponseBody.List.builder()
                .body("aGVsbG8=")
                .build();

        var record = AliyunConverters.toMessageRecord(data);

        assertThat(record.getBody()).isEqualTo("hello");
        assertThat(record.getBodyEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void toMessageRecordShouldKeepPlainTextBodyAsText() {
        ListMessagesResponseBody.List data = ListMessagesResponseBody.List.builder()
                .body("not-base64!!!")
                .build();

        var record = AliyunConverters.toMessageRecord(data);

        assertThat(record.getBody()).isEqualTo("not-base64!!!");
        assertThat(record.getBodyEncoding()).isEqualTo("TEXT");
    }

    @Test
    void toMessageRecordShouldReturnNullKeyWhenMessageKeysMissing() {
        ListMessagesResponseBody.List nullKeys = ListMessagesResponseBody.List.builder()
                .messageKeys(null)
                .build();
        ListMessagesResponseBody.List emptyKeys = ListMessagesResponseBody.List.builder()
                .messageKeys(Collections.emptyList())
                .build();

        assertThat(AliyunConverters.toMessageRecord(nullKeys).getKey()).isNull();
        assertThat(AliyunConverters.toMessageRecord(emptyKeys).getKey()).isNull();
    }
}
