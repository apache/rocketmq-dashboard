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
package org.apache.rocketmq.studio.cluster.lifecycle;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessLifecycleOperationExecutorTest {

    @Test
    void dispatchesAnAllowlistedOperationWithStructuredArguments() {
        LifecycleProperties properties = enabledProperties(LifecycleOperation.BROKER_RESTART);
        List<String> capturedCommand = new ArrayList<>();
        LifecycleProcessRunner runner = (command, workingDirectory, timeout, maxOutputBytes) -> {
            capturedCommand.addAll(command);
            return LifecycleProcessResult.success("accepted");
        };

        LifecycleOperationResult result = new ProcessLifecycleOperationExecutor(properties, runner)
                .execute(new LifecycleOperationRequest(
                        LifecycleOperation.BROKER_RESTART,
                        "cluster-1",
                        "broker-a",
                        "10.0.0.1:10911",
                        null,
                        "request-1"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.requestId()).isEqualTo("request-1");
        assertThat(capturedCommand).containsExactly(
                "/opt/rocketmq/lifecycle",
                "broker-restart",
                "--cluster-id", "cluster-1",
                "--target", "broker-a",
                "--target-address", "10.0.0.1:10911",
                "--request-id", "request-1");
    }

    @Test
    void rejectsDisabledExecutionBeforeStartingAProcess() {
        LifecycleProperties properties = enabledProperties(LifecycleOperation.BROKER_RESTART);
        properties.setEnabled(false);
        boolean[] called = {false};
        LifecycleProcessRunner runner = (command, workingDirectory, timeout, maxOutputBytes) -> {
            called[0] = true;
            return LifecycleProcessResult.success("unexpected");
        };

        assertThatThrownBy(() -> new ProcessLifecycleOperationExecutor(properties, runner)
                .execute(request(LifecycleOperation.BROKER_RESTART)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(501))
                .hasMessageContaining("not configured");
        assertThat(called[0]).isFalse();
    }

    @Test
    void rejectsAnOperationThatIsNotAllowlisted() {
        LifecycleProperties properties = enabledProperties(LifecycleOperation.BROKER_RESTART);
        LifecycleProcessRunner runner = (command, workingDirectory, timeout, maxOutputBytes) ->
                LifecycleProcessResult.success("unexpected");

        assertThatThrownBy(() -> new ProcessLifecycleOperationExecutor(properties, runner)
                .execute(request(LifecycleOperation.PROXY_RESTART)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(501))
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void mapsNonZeroExitToGatewayFailureAndBoundsOutput() {
        LifecycleProperties properties = enabledProperties(LifecycleOperation.BROKER_RESTART);
        properties.setMaxOutputBytes(8);
        LifecycleProcessRunner runner = (command, workingDirectory, timeout, maxOutputBytes) ->
                LifecycleProcessResult.failure(7, "0123456789");

        assertThatThrownBy(() -> new ProcessLifecycleOperationExecutor(properties, runner)
                .execute(request(LifecycleOperation.BROKER_RESTART)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502))
                .hasMessageContaining("01234567");
    }

    @Test
    void mapsTimeoutToGatewayTimeout() {
        LifecycleProperties properties = enabledProperties(LifecycleOperation.BROKER_RESTART);
        LifecycleProcessRunner runner = (command, workingDirectory, timeout, maxOutputBytes) ->
                LifecycleProcessResult.timedOut("timed out");

        assertThatThrownBy(() -> new ProcessLifecycleOperationExecutor(properties, runner)
                .execute(request(LifecycleOperation.BROKER_RESTART)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(504))
                .hasMessageContaining("timed out");
    }

    @Test
    void requiresAnUpgradeVersion() {
        LifecycleProperties properties = enabledProperties(LifecycleOperation.NAMESERVER_UPGRADE);
        LifecycleProcessRunner runner = (command, workingDirectory, timeout, maxOutputBytes) ->
                LifecycleProcessResult.success("unexpected");

        LifecycleOperationRequest request = new LifecycleOperationRequest(
                LifecycleOperation.NAMESERVER_UPGRADE,
                "cluster-1",
                "10.0.0.20:9876",
                null,
                " ",
                "request-1");

        assertThatThrownBy(() -> new ProcessLifecycleOperationExecutor(properties, runner).execute(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400))
                .hasMessageContaining("targetVersion");
    }

    private static LifecycleProperties enabledProperties(LifecycleOperation operation) {
        LifecycleProperties properties = new LifecycleProperties();
        properties.setEnabled(true);
        properties.setExecutable("/opt/rocketmq/lifecycle");
        properties.setTimeout(Duration.ofSeconds(3));
        properties.setMaxOutputBytes(128);
        properties.setAllowedOperations(new LinkedHashSet<>(List.of(operation)));
        return properties;
    }

    private static LifecycleOperationRequest request(LifecycleOperation operation) {
        return new LifecycleOperationRequest(operation, "cluster-1", "target", null, null, "request-1");
    }
}
