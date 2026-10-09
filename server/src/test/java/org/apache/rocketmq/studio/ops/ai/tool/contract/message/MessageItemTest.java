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
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MessageItem}: one message row of a query result listing. The body truncation pair is
 * the honest-degradation seam - bodyTruncated must travel with the body so a consumer of the
 * listing knows the payload is a prefix, not the whole message.
 */
class MessageItemTest {

    @Test
    void mapsEveryMessageField() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-1").topic("orders").tag("created").key("order-42")
                .storeTime(1_800_000_000_000L)
                .storeHost("10.0.0.1:10911").bornHost("10.0.0.9:52318")
                .body("{\"amount\":42}").bodyEncoding("UTF-8")
                .bodyTruncated(false).size(15)
                .build();

        MessageItem item = MessageItem.from(message);

        assertThat(item.msgId()).isEqualTo("msg-1");
        assertThat(item.topic()).isEqualTo("orders");
        assertThat(item.tag()).isEqualTo("created");
        assertThat(item.key()).isEqualTo("order-42");
        assertThat(item.storeTime()).isEqualTo(1_800_000_000_000L);
        assertThat(item.storeHost()).isEqualTo("10.0.0.1:10911");
        assertThat(item.bornHost()).isEqualTo("10.0.0.9:52318");
        assertThat(item.body()).isEqualTo("{\"amount\":42}");
        assertThat(item.bodyEncoding()).isEqualTo("UTF-8");
        assertThat(item.bodyTruncated()).isFalse();
        assertThat(item.size()).isEqualTo(15);
    }

    @Test
    void aTruncatedBodyCarriesItsFlag() {
        // the truncated flag is what tells the consumer the body is a prefix
        // capped by the 32 KiB transport limit, not the whole payload
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-2").topic("orders")
                .body("AAAA...").bodyEncoding("UTF-8")
                .bodyTruncated(true).size(32768)
                .build();

        MessageItem item = MessageItem.from(message);

        assertThat(item.bodyTruncated()).isTrue();
        assertThat(item.size()).isEqualTo(32768);
    }

    @Test
    void anOptionalFieldStaysNullNotADefault() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-3").topic("orders").build();

        MessageItem item = MessageItem.from(message);

        assertThat(item.tag()).isNull();
        assertThat(item.key()).isNull();
        assertThat(item.body()).isNull();
        assertThat(item.storeTime()).isZero();
    }
}
