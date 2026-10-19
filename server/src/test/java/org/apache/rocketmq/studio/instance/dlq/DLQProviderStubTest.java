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
package org.apache.rocketmq.studio.instance.dlq;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DLQProviderStubTest {

    private final DLQProviderStub stub = new DLQProviderStub();

    @Test
    void listDLQGroupsFailsClosedWith501() {
        assertThatThrownBy(() -> stub.listDLQGroups("inst-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void pagedListDLQGroupsFailsClosedWith501() {
        assertThatThrownBy(() -> stub.listDLQGroups("inst-1", "search", 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void resendByTimeWindowFailsClosedWith501() {
        assertThatThrownBy(() -> stub.resendMessages("inst-1", "orders", 0L, 1L, "target"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void exportMessagesFailsClosedWith501() {
        assertThatThrownBy(() -> stub.exportMessages("inst-1", "orders", 0L, 1L, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void listMessagesFailsClosedWith501() {
        assertThatThrownBy(() -> stub.listMessages("inst-1", "orders", 0L, 1L, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void resendSelectedMessagesFailsClosedWith501() {
        assertThatThrownBy(() -> stub.resendMessages("inst-1", "orders", List.of("msg-1"), "target"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void exportExcelFailsClosedWith501() {
        assertThatThrownBy(() -> stub.exportExcel("inst-1", "orders", 0L, 1L, List.of("msg-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ provider is not configured")
                .extracting("code").isEqualTo(501);
    }

    @Test
    void theStubSatisfiesTheProviderContract() {
        // A deployment without a real DLQ provider fails closed on every operation
        // with the same structured not-configured error.
        assertThat(stub).isInstanceOf(DLQProvider.class);
    }
}
