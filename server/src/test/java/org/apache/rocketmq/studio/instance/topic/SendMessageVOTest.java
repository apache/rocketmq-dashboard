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

package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SendMessageVO}, the answer of the dashboard's message send: the broker-side
 * message id, the offset message id and the send time. The send dialog shows exactly these
 * three, so all construction paths must agree and the no-args path must read as an empty
 * result rather than a defaulted one.
 */
class SendMessageVOTest {

    @Test
    void aSendResultCarriesBothIdsAndTheSendTime() {
        SendMessageVO result = SendMessageVO.builder()
                .msgId("AC11000100001A3B0000000000000000")
                .offsetMsgId("0A6B2D5700002A3B0000000000000000")
                .sendTime(1700000000000L)
                .build();

        assertThat(result.getMsgId()).isEqualTo("AC11000100001A3B0000000000000000");
        assertThat(result.getOffsetMsgId()).isEqualTo("0A6B2D5700002A3B0000000000000000");
        assertThat(result.getSendTime()).isEqualTo(1700000000000L);
    }

    @Test
    void allConstructionPathsAgree() {
        SendMessageVO built = SendMessageVO.builder()
                .msgId("AC11000100001A3B0000000000000000")
                .offsetMsgId("0A6B2D5700002A3B0000000000000000")
                .sendTime(1700000000000L)
                .build();
        SendMessageVO allArgs = new SendMessageVO(
                "AC11000100001A3B0000000000000000", 1700000000000L, "0A6B2D5700002A3B0000000000000000");
        SendMessageVO defaulted = new SendMessageVO();
        defaulted.setMsgId("AC11000100001A3B0000000000000000");
        defaulted.setSendTime(1700000000000L);
        defaulted.setOffsetMsgId("0A6B2D5700002A3B0000000000000000");

        assertThat(built).isEqualTo(allArgs).isEqualTo(defaulted);

        SendMessageVO empty = new SendMessageVO();
        assertThat(empty.getMsgId()).isNull();
        assertThat(empty.getOffsetMsgId()).isNull();
        assertThat(empty.getSendTime()).isZero();
    }
}
