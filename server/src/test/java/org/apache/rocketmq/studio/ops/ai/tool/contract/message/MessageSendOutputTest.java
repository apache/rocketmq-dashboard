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

import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MessageSendOutput}, the AI tool's answer for a message send: the broker-side
 * message id, the offset message id and the send time. The {@code from} factory is the only
 * bridge from the service's {@link SendMessageVO}, so it must carry all three fields across
 * without mixing the two ids up.
 */
class MessageSendOutputTest {

    @Test
    void fromCarriesEveryFieldAcross() {
        SendMessageVO source = SendMessageVO.builder()
                .msgId("AC11000100001A3B0000000000000000")
                .offsetMsgId("0A6B2D5700002A3B0000000000000000")
                .sendTime(1700000000000L)
                .build();

        MessageSendOutput output = MessageSendOutput.from(source);

        assertThat(output.msgId()).isEqualTo("AC11000100001A3B0000000000000000");
        assertThat(output.offsetMsgId()).isEqualTo("0A6B2D5700002A3B0000000000000000");
        assertThat(output.sendTime()).isEqualTo(1700000000000L);
    }

    @Test
    void identicalOutputsAreValueEqual() {
        MessageSendOutput left = new MessageSendOutput(
                "AC11000100001A3B0000000000000000", "0A6B2D5700002A3B0000000000000000", 1700000000000L);
        MessageSendOutput right = MessageSendOutput.from(SendMessageVO.builder()
                .msgId("AC11000100001A3B0000000000000000")
                .offsetMsgId("0A6B2D5700002A3B0000000000000000")
                .sendTime(1700000000000L)
                .build());

        assertThat(left).isEqualTo(right);
        assertThat(left).isNotEqualTo(new MessageSendOutput(
                "AC11000100001A3B0000000000000000", "0A6B2D5700002A3B0000000000000000", 1L));
    }
}
