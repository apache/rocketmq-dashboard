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
package org.apache.rocketmq.studio.common;

import org.apache.rocketmq.studio.cluster.client.ClientProviderStub;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.dlq.DLQProviderStub;
import org.apache.rocketmq.studio.instance.group.ConsumerDiagnosticsProviderStub;
import org.apache.rocketmq.studio.instance.message.MessageProviderStub;
import org.apache.rocketmq.studio.ops.dashboard.DashboardProviderStub;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that all conditional provider stubs throw BusinessException(501)
 * when no real provider is configured.
 */
class ProviderStubContractTest {

    static Stream<Arguments> stubInvocations() {
        ClientProviderStub client = new ClientProviderStub();
        DLQProviderStub dlq = new DLQProviderStub();
        MessageProviderStub message = new MessageProviderStub();
        ConsumerDiagnosticsProviderStub diagnostics = new ConsumerDiagnosticsProviderStub();
        DashboardProviderStub dashboard = new DashboardProviderStub();

        return Stream.of(
                Arguments.of("ClientProviderStub.findConnections",
                        (Runnable) () -> client.findConnections("i", "c", "Producer")),
                Arguments.of("ClientProviderStub.findProducerConnections",
                        (Runnable) () -> client.findProducerConnections("i", "t", "pg")),
                Arguments.of("ClientProviderStub.findProducerGroups",
                        (Runnable) () -> client.findProducerGroups("i", "t", "pg", 20)),
                Arguments.of("DLQProviderStub.listDLQGroups",
                        (Runnable) () -> dlq.listDLQGroups("i")),
                Arguments.of("DLQProviderStub.listDLQGroups(paged)",
                        (Runnable) () -> dlq.listDLQGroups("i", "s", 1, 20)),
                Arguments.of("DLQProviderStub.resendMessages",
                        (Runnable) () -> dlq.resendMessages("i", "g", 1L, 2L, "t")),
                Arguments.of("DLQProviderStub.exportMessages",
                        (Runnable) () -> dlq.exportMessages("i", "g", 1L, 2L, 100)),
                Arguments.of("MessageProviderStub.queryMessages",
                        (Runnable) () -> message.queryMessages("i", "t", null, null, null, null, null)),
                Arguments.of("MessageProviderStub.getMessageTrace",
                        (Runnable) () -> message.getMessageTrace("i", "m", "t")),
                Arguments.of("ConsumerDiagnosticsProviderStub.getConsumerStack",
                        (Runnable) () -> diagnostics.getConsumerStack("i", "g", "c")),
                Arguments.of("DashboardProviderStub.getDashboardData",
                        (Runnable) dashboard::getDashboardData)
        );
    }

    @ParameterizedTest(name = "{0} should throw 501")
    @MethodSource("stubInvocations")
    void stubMethodShouldThrow501WhenProviderIsMissingTest(String name, Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }
}
