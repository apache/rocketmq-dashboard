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
    void toMessageRecordShouldHandleSingleAndAllBlankKeys() {
        ListMessagesResponseBody.List single = ListMessagesResponseBody.List.builder()
                .messageKeys(java.util.Collections.singletonList("key-a"))
                .build();
        assertThat(AliyunConverters.toMessageRecord(single).getKey()).isEqualTo("key-a");

        ListMessagesResponseBody.List allBlank = ListMessagesResponseBody.List.builder()
                .messageKeys(Arrays.asList(null, " ", null))
                .build();
        assertThat(AliyunConverters.toMessageRecord(allBlank).getKey()).isNull();
    }

    @Test
    void toMessageRecordShouldKeepInternalSpacesInsideKeys() {
        ListMessagesResponseBody.List data = ListMessagesResponseBody.List.builder()
                .messageKeys(Arrays.asList("k a", "key-b"))
                .build();

        assertThat(AliyunConverters.toMessageRecord(data).getKey()).isEqualTo("k a key-b");
    }
}
