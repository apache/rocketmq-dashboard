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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.CliProcessEnvironment;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Answers "is this CLI on PATH in the runtime image?" by running {@code sh -c "command -v <binary>"}
 * in the same isolated child environment every other CLI subprocess gets.
 *
 * <p>Extracted from {@code CliAgentProvider} so the agent CLIs ({@code claude}, {@code qodercli})
 * and the {@code rmqctl} MCP transport share one implementation: a missing {@code rmqctl} must
 * degrade a conversation to plain chat rather than fail it, and that decision needs the exact same
 * probe, timeout and interrupt handling as the provider availability check that feeds
 * {@code llm.provider.cli_missing}.
 *
 * <p>The probe is cheap but not free (one {@code sh} fork, up to {@value #PROBE_TIMEOUT_SECONDS} s),
 * so callers that answer a UI question on every request are expected to cache the result themselves.
 *
 * <h2>Why the environment is injected as a callback</h2>
 * {@code CliProcessEnvironment.apply} is package-private inside {@code ops.ai} and is the seam its
 * tests override to record what crossed the boundary. Passing it in as an {@link EnvironmentApplier}
 * keeps that seam in the call path — a probe that reached for a public method instead would silently
 * bypass the recording environment and the isolation tests would keep passing while testing nothing.
 */
@Slf4j
public final class CliBinaryProbe {

    /** Wall-clock budget for the probe. A PATH lookup does not need longer, and a hung shell must
     * not be able to stall the caller. */
    private static final long PROBE_TIMEOUT_SECONDS = 5;

    /**
     * A bare POSIX-style binary name. The probe interpolates this value into {@code sh -c}, so
     * anything a shell could read as syntax — a space, a command separator, a substitution, a
     * glob, a path separator — has to be refused rather than escaped: no quoting survives being
     * handed to a command line somebody else assembled. Every caller today passes a compile-time
     * constant ({@code claude}, {@code qodercli}, {@code rmqctl}); this is what keeps that true by
     * construction instead of by convention.
     */
    private static final Pattern BINARY_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");

    private final EnvironmentApplier environmentApplier;
    private final ProcessStarter processStarter;

    /**
     * Production constructor: the isolated environment is applied through its public entry point and
     * the probe process is really started.
     */
    public CliBinaryProbe(CliProcessEnvironment processEnvironment) {
        this(processEnvironment::applyIsolated, ProcessBuilder::start);
    }

    /** Visible for injection: callers inside {@code ops.ai} pass their own overridable seam. */
    public CliBinaryProbe(EnvironmentApplier environmentApplier, ProcessStarter processStarter) {
        this.environmentApplier = environmentApplier;
        this.processStarter = processStarter;
    }

    /**
     * @return true when {@code command -v <binaryName>} exits 0 within the probe budget. A timeout,
     *     an I/O failure and an interrupt all mean "not available": availability is a precondition
     *     for spawning a CLI, and guessing yes is worse than guessing no. A name that is not a bare
     *     identifier (see {@link #BINARY_NAME}) is refused the same way, before a process is built.
     */
    public boolean isAvailable(String binaryName) {
        if (binaryName == null || !BINARY_NAME.matcher(binaryName).matches()) {
            // Fail closed, and log without echoing the value: this is the branch a hostile name
            // would reach, and a log line is not the place to hand it back unescaped.
            log.warn("refusing to probe a binary name that is not a bare identifier");
            return false;
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", "command -v " + binaryName);
            environmentApplier.apply(builder, Map.of());
            process = processStarter.start(builder.redirectErrorStream(true));
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                log.debug("availability probe for {} timed out after {}s", binaryName, PROBE_TIMEOUT_SECONDS);
                process.destroyForcibly();
            }
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("availability probe for {} failed: {}", binaryName, exception.toString());
            return false;
        }
    }

    /** Applies the isolated child environment to a probe process builder. */
    @FunctionalInterface
    public interface EnvironmentApplier {

        void apply(ProcessBuilder builder, Map<String, String> providerEnvironment);
    }

    /** Starts the probe process. A seam, so a hung or interrupted probe is testable. */
    @FunctionalInterface
    public interface ProcessStarter {

        Process start(ProcessBuilder builder) throws IOException;
    }
}
