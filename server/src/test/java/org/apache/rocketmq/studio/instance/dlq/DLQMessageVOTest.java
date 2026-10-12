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
package org.apache.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the dead-letter row of {@link DLQMessageVO}: the message identity and routing, the body
 * with its base64 form, the properties with their own truncation flag - so the UI can say
 * "showing the first N properties" instead of implying completeness.
 */
class DLQMessageVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        DLQMessageVO message = DLQMessageVO.builder()
                .msgId("msg-1")
                .topic("%DLQ%order-group")
                .queueId(2)
                .offset(1024L)
                .storeTime(1784112606000L)
                .reconsumeTimes(16)
                .keys("order-42")
                .body("hello")
                .bodyBase64("aGVsbG8=")
                .properties(Map.of("RETRY_TOPIC", "orders"))
                .propertiesTruncated(true)
                .build();

        assertThat(message.getMsgId()).isEqualTo("msg-1");
        assertThat(message.getTopic()).isEqualTo("%DLQ%order-group");
        assertThat(message.getQueueId()).isEqualTo(2);
        assertThat(message.getOffset()).isEqualTo(1024L);
        assertThat(message.getStoreTime()).isEqualTo(1784112606000L);
        assertThat(message.getReconsumeTimes()).isEqualTo(16);
        assertThat(message.getKeys()).isEqualTo("order-42");
        assertThat(message.getBody()).isEqualTo("hello");
        assertThat(message.getBodyBase64()).isEqualTo("aGVsbG8=");
        assertThat(message.getProperties()).containsEntry("RETRY_TOPIC", "orders");
        assertThat(message.isPropertiesTruncated()).isTrue();
    }

    @Test
    void aFreshRowIsNotTruncated() {
        DLQMessageVO message = new DLQMessageVO();
        assertThat(message.isPropertiesTruncated()).isFalse();
        assertThat(message.getReconsumeTimes()).isZero();
        assertThat(message.getProperties()).isNull();
    }
}
