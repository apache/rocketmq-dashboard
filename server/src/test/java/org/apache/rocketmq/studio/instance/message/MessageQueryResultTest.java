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
package org.apache.rocketmq.studio.instance.message;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the message query result of {@link MessageQueryResult}: the message list is null-safe
 * and defensively copied, the complete/truncated factories set the honesty flag, and truncation
 * means "the limit was hit", not "the query failed".
 */
class MessageQueryResultTest {

    @Test
    void aNullMessageListNormalisesToEmpty() {
        MessageQueryResult result = new MessageQueryResult(null, false);
        assertThat(result.messages()).isEmpty();
        assertThat(result.mayBeTruncated()).isFalse();
    }

    @Test
    void theMessageListIsDefensivelyCopied() {
        List<MessageRecordVO> source = new ArrayList<>();
        source.add(MessageRecordVO.builder().msgId("msg-1").build());
        MessageQueryResult result = MessageQueryResult.complete(source);

        source.add(MessageRecordVO.builder().msgId("msg-2").build());

        assertThat(result.messages()).hasSize(1);
        assertThat(result.mayBeTruncated()).isFalse();
    }

    @Test
    void theFactoriesSetTheHonestyFlag() {
        List<MessageRecordVO> messages = List.of(MessageRecordVO.builder().msgId("msg-1").build());
        assertThat(MessageQueryResult.complete(messages).mayBeTruncated()).isFalse();
        assertThat(MessageQueryResult.truncated(messages).mayBeTruncated()).isTrue();
    }
}
