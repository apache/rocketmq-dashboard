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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MessageQueryResult}, the message-query outcome that carries the
 * provider truncation signal so callers can distinguish an exhausted query from one that
 * hit the provider's bounded result budget.
 */
class MessageQueryResultTest {

    private static MessageRecordVO record(String msgId) {
        return MessageRecordVO.builder().msgId(msgId).build();
    }

    @Test
    void factoriesCarryTheTruncationSignal() {
        List<MessageRecordVO> rows = List.of(record("a"));

        assertThat(MessageQueryResult.complete(rows).mayBeTruncated()).isFalse();
        assertThat(MessageQueryResult.truncated(rows).mayBeTruncated()).isTrue();
    }

    @Test
    void defensivelyCopiesTheMessageRows() {
        List<MessageRecordVO> mutable = new ArrayList<>(List.of(record("a")));
        MessageQueryResult result = MessageQueryResult.complete(mutable);

        mutable.add(record("b"));
        assertThat(result.messages()).containsExactly(record("a"));
        assertThatThrownBy(() -> result.messages().add(record("c")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void normalizesNullRowsToAnEmptyList() {
        MessageQueryResult result = new MessageQueryResult(null, false);

        assertThat(result.messages()).isEmpty();
        assertThat(result.mayBeTruncated()).isFalse();
    }
}
