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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the message record view of {@link MessageRecordVO}: one stored message as the UI shows
 * it - identity, routing (broker/queue/offset), the possibly-truncated body with its encoding,
 * and the store time. The truncation flag and the store-time default are the subtle parts: a
 * record without a store time reads as epoch zero, and bodyTruncated defaults to false so a
 * present body is shown whole unless the producer says otherwise.
 */
class MessageRecordVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        MessageRecordVO record = MessageRecordVO.builder()
                .msgId("msg-1")
                .topic("topic-1")
                .tag("tag-a")
                .key("key-1")
                .brokerName("broker-a")
                .queueId(3)
                .queueOffset(1024L)
                .body("hello")
                .bodyEncoding("UTF-8")
                .bodyTruncated(true)
                .storeTime(1784112606000L)
                .bornHost("10.0.0.1")
                .build();

        assertThat(record.getMsgId()).isEqualTo("msg-1");
        assertThat(record.getTopic()).isEqualTo("topic-1");
        assertThat(record.getTag()).isEqualTo("tag-a");
        assertThat(record.getKey()).isEqualTo("key-1");
        assertThat(record.getBrokerName()).isEqualTo("broker-a");
        assertThat(record.getQueueId()).isEqualTo(3);
        assertThat(record.getQueueOffset()).isEqualTo(1024L);
        assertThat(record.getBody()).isEqualTo("hello");
        assertThat(record.getBodyEncoding()).isEqualTo("UTF-8");
        assertThat(record.isBodyTruncated()).isTrue();
        assertThat(record.getStoreTime()).isEqualTo(1784112606000L);
        assertThat(record.getBornHost()).isEqualTo("10.0.0.1");
    }

    @Test
    void aFreshRecordShowsAnUntruncatedBodyAndEpochStoreTime() {
        MessageRecordVO record = new MessageRecordVO();
        assertThat(record.isBodyTruncated()).isFalse();
        assertThat(record.getStoreTime()).isZero();
    }
}
