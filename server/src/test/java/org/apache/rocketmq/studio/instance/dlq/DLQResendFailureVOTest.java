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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the DLQ resend failure row of {@link DLQResendFailureVO}: one failed message - the
 * identity (msgId) with the target topic and the reason.
 */
class DLQResendFailureVOTest {

    @Test
    void theFailureRoundTripsThroughTheBuilder() {
        DLQResendFailureVO failure = DLQResendFailureVO.builder()
                .msgId("msg-1")
                .targetTopic("orders")
                .reason("offset already consumed")
                .build();

        assertThat(failure.getMsgId()).isEqualTo("msg-1");
        assertThat(failure.getTargetTopic()).isEqualTo("orders");
        assertThat(failure.getReason()).isEqualTo("offset already consumed");
    }
}
