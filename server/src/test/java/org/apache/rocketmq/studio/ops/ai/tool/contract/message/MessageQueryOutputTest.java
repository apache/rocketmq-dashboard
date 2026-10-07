/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageQueryOutputTest {

    private static MessageRecordVO record(String msgId, String body) {
        return MessageRecordVO.builder()
                .msgId(msgId).topic("orders").tag("t").key("k")
                .storeTime(1234L).storeHost("s").bornHost("b")
                .body(body).bodyEncoding("UTF-8").bodyTruncated(true)
                .size(10).build();
    }

    @Test
    void fromPageCountsSkippedMessagesBeyondTheReturnedWindow() {
        MessageQueryPageVO page = MessageQueryPageVO.builder()
                .items(List.of(record("m1", "body")))
                .total(51)
                .page(3).size(1)
                .resultMayBeTruncated(false)
                .build();

        MessageQueryOutput output = MessageQueryOutput.fromPage(page, true);

        assertThat(output.skippedCount()).isEqualTo(50);
        // Even without the provider's own truncation flag, a window that skipped
        // messages must report the result as possibly truncated.
        assertThat(output.resultMayBeTruncated()).isTrue();
    }

    @Test
    void fromPagePreservesTheProviderTruncationFlag() {
        MessageQueryPageVO page = MessageQueryPageVO.builder()
                .items(List.of(record("m1", "body")))
                .total(1)
                .resultMayBeTruncated(true)
                .build();

        MessageQueryOutput output = MessageQueryOutput.fromPage(page, true);

        assertThat(output.skippedCount()).isZero();
        assertThat(output.resultMayBeTruncated()).isTrue();
    }

    @Test
    void fromPageReportsANonSkippedCompleteWindowAsNotTruncated() {
        MessageQueryPageVO page = MessageQueryPageVO.builder()
                .items(List.of(record("m1", "body"), record("m2", "body")))
                .total(2)
                .resultMayBeTruncated(false)
                .build();

        MessageQueryOutput output = MessageQueryOutput.fromPage(page, true);

        assertThat(output.skippedCount()).isZero();
        assertThat(output.resultMayBeTruncated()).isFalse();
    }

    @Test
    void fromPageClampsANegativeSkippedCountToZero() {
        MessageQueryPageVO page = MessageQueryPageVO.builder()
                .items(List.of(record("m1", "body")))
                .total(0)
                .resultMayBeTruncated(false)
                .build();

        MessageQueryOutput output = MessageQueryOutput.fromPage(page, true);

        assertThat(output.skippedCount()).isZero();
        assertThat(output.resultMayBeTruncated()).isFalse();
    }

    @Test
    void fromUniqueKeyTruncatesTheListToTheLimitAndCountsTheRest() {
        List<MessageRecordVO> messages = List.of(
                record("m1", "b"), record("m2", "b"), record("m3", "b"));

        MessageQueryOutput output = MessageQueryOutput.fromUniqueKey(messages, 2, true);

        assertThat(output.items()).extracting(MessageQueryOutput.Item::msgId)
                .containsExactly("m1", "m2");
        assertThat(output.skippedCount()).isEqualTo(1);
        assertThat(output.resultMayBeTruncated()).isTrue();
    }

    @Test
    void fromUniqueKeyWithALimitBeyondTheListKeepsEverything() {
        List<MessageRecordVO> messages = List.of(record("m1", "b"));

        MessageQueryOutput output = MessageQueryOutput.fromUniqueKey(messages, 10, true);

        assertThat(output.items()).hasSize(1);
        assertThat(output.skippedCount()).isZero();
        assertThat(output.resultMayBeTruncated()).isFalse();
    }

    @Test
    void bodiesAreIncludedOnlyWhenAskedFor() {
        List<MessageRecordVO> messages = List.of(record("m1", "secret-body"));

        MessageQueryOutput.Item withBody = MessageQueryOutput.fromUniqueKey(messages, 1, true).items().get(0);
        MessageQueryOutput.Item withoutBody = MessageQueryOutput.fromUniqueKey(messages, 1, false).items().get(0);

        assertThat(withBody.body()).isEqualTo("secret-body");
        assertThat(withBody.bodyEncoding()).isEqualTo("UTF-8");
        assertThat(withBody.bodyTruncated()).isTrue();
        assertThat(withoutBody.body()).isNull();
        assertThat(withoutBody.bodyEncoding()).isNull();
        assertThat(withoutBody.bodyTruncated()).isNull();
        // The identity fields survive either way.
        assertThat(withoutBody.msgId()).isEqualTo("m1");
        assertThat(withoutBody.size()).isEqualTo(10);
    }
}
