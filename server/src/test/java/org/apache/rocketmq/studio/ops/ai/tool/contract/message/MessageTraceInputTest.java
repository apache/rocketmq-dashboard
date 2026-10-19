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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MessageTraceInput}: the trace query request shape. The convenience constructor
 * defaults the custom trace topic to null (the broker default).
 */
class MessageTraceInputTest {

    @Test
    void theConvenienceConstructorDefaultsTheTraceTopicToNull() {
        MessageTraceInput input = new MessageTraceInput("instance-a", "orders", "msg-1");

        assertThat(input.instanceId()).isEqualTo("instance-a");
        assertThat(input.topicName()).isEqualTo("orders");
        assertThat(input.msgId()).isEqualTo("msg-1");
        assertThat(input.traceTopicName()).isNull();
    }

    @Test
    void theFullConstructorCarriesTheCustomTraceTopic() {
        MessageTraceInput input = new MessageTraceInput("instance-a", "orders", "msg-1", "rmq_trace");

        assertThat(input.traceTopicName()).isEqualTo("rmq_trace");
    }
}
