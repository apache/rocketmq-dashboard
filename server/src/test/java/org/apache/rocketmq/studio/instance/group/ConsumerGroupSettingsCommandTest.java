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
package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ConsumerGroupSettingsCommand}: the write-side command for consumer group settings
 * carries every knob the broker accepts, with the retry knobs as primitive ints (always
 * supplied) and the consume toggles as boxed booleans (null means "leave unchanged").
 */
class ConsumerGroupSettingsCommandTest {

    @Test
    void everyKnobRoundTrips() {
        ConsumerGroupSettingsCommand command = new ConsumerGroupSettingsCommand(
                2, 16, true, false, true);

        assertThat(command.retryQueueNums()).isEqualTo(2);
        assertThat(command.retryMaxTimes()).isEqualTo(16);
        assertThat(command.consumeEnable()).isTrue();
        assertThat(command.consumeMessageOrderly()).isFalse();
        assertThat(command.consumeBroadcastEnable()).isTrue();
    }

    @Test
    void theConsumeTogglesMayStayUnset() {
        ConsumerGroupSettingsCommand command = new ConsumerGroupSettingsCommand(1, 5, null, null, null);

        assertThat(command.consumeEnable()).isNull();
        assertThat(command.consumeMessageOrderly()).isNull();
        assertThat(command.consumeBroadcastEnable()).isNull();
    }
}
