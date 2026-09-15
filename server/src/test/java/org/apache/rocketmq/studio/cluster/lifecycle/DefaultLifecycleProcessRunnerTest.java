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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DefaultLifecycleProcessRunnerTest {

    private DefaultLifecycleProcessRunner runner;

    @BeforeEach
    void setUp() {
        runner = new DefaultLifecycleProcessRunner();
    }

    @AfterEach
    void tearDown() {
        runner.shutdown();
    }

    @Test
    void capturesExitCodeAndCombinedOutput() {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));

        LifecycleProcessResult result = runner.run(
                List.of("/bin/sh", "-c", "printf success"), null, Duration.ofSeconds(2), 128);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("success");
        assertThat(result.timedOut()).isFalse();
        assertThat(result.outputTruncated()).isFalse();
    }

    @Test
    void boundsOutputWhileDrainingTheProcessStream() {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));

        LifecycleProcessResult result = runner.run(
                List.of("/bin/sh", "-c", "printf 0123456789"), null, Duration.ofSeconds(2), 4);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("0123");
        assertThat(result.outputTruncated()).isTrue();
    }

    @Test
    void terminatesAProcessThatExceedsTheTimeout() {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")));

        LifecycleProcessResult result = runner.run(
                List.of("/bin/sh", "-c", "sleep 2"), null, Duration.ofMillis(50), 128);

        assertThat(result.timedOut()).isTrue();
    }
}
