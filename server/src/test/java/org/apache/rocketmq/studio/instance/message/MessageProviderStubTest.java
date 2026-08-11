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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageProviderStubTest {

    private final MessageProviderStub provider = new MessageProviderStub();

    @Test
    void queryMessagesShouldFailExplicitlyWhenRealProviderIsMissing() {
        assertThatThrownBy(() -> provider.queryMessages("instance-a", "orders", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code")
                .isEqualTo(501);
    }

    @Test
    void getMessageTraceShouldFailExplicitlyWhenRealProviderIsMissing() {
        assertThatThrownBy(() -> provider.getMessageTrace("instance-a", "msg-001", "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query provider is not configured")
                .extracting("code")
                .isEqualTo(501);
    }
}
