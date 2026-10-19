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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageProviderStubTest {

    private final MessageProviderStub stub = new MessageProviderStub();

    @Test
    void queryMessagesFailsClosedWith501() {
        assertThatThrownBy(() -> stub.queryMessages("inst-1", "orders", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void queryMessageByUniqueKeyFailsClosedWith501() {
        assertThatThrownBy(() -> stub.queryMessageByUniqueKey("inst-1", "orders", "key-1", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void getMessageTraceFailsClosedWith501() {
        assertThatThrownBy(() -> stub.getMessageTrace("inst-1", "msg-1", "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void getQueueOffsetsFailsClosedWith501() {
        assertThatThrownBy(() -> stub.getQueueOffsets("inst-1", "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void pullMessageAtOffsetFailsClosedWith501() {
        assertThatThrownBy(() -> stub.pullMessageAtOffset("inst-1", "orders", "broker-a", 0, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void theStubSatisfiesTheProviderContract() {
        // The stub IS a MessageProvider: a deployment without a real provider fails closed
        // everywhere with a structured 501 rather than an accidental runtime error.
        assertThat(stub).isInstanceOf(MessageProvider.class);
    }
}
