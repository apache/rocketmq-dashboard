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

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs a fixed argument vector without shell expansion and with bounded output. */
@Component
final class DefaultLifecycleProcessRunner implements LifecycleProcessRunner {

    private static final long PROCESS_STOP_GRACE_SECONDS = 1;

    private final ExecutorService outputReaders = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public LifecycleProcessResult run(List<String> command, String workingDirectory,
                                      Duration timeout, int maxOutputBytes) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (StringUtils.hasText(workingDirectory)) {
            builder.directory(Path.of(workingDirectory.trim()).toFile());
        }

        final Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start lifecycle executable", exception);
        }
        closeInput(process);

        Future<OutputCapture> output = outputReaders.submit(
                () -> readOutput(process.getInputStream(), maxOutputBytes));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stop(process);
            OutputCapture capture = awaitOutput(output);
            return new LifecycleProcessResult(-1, capture.value(), true, capture.truncated());
        }
        if (!finished) {
            stop(process);
            OutputCapture capture = awaitOutput(output);
            return new LifecycleProcessResult(-1, capture.value(), true, capture.truncated());
        }

        OutputCapture capture = awaitOutput(output);
        return new LifecycleProcessResult(process.exitValue(), capture.value(), false, capture.truncated());
    }

    private static void closeInput(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            stop(process);
            throw new IllegalStateException("Failed to close lifecycle executable input", exception);
        }
    }

    private static void stop(Process process) {
        ProcessHandle root = process.toHandle();
        List<ProcessHandle> descendants = root.descendants().toList();
        root.destroy();
        destroy(descendants, false);
        try {
            process.waitFor(PROCESS_STOP_GRACE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            // A wrapper may exit promptly while a child ignores TERM and keeps running.
            destroy(descendants, true);
            root.descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
        if (root.isAlive()) {
            root.destroyForcibly();
        }
    }

    private static void destroy(List<ProcessHandle> processes, boolean forcibly) {
        processes.stream()
                .filter(ProcessHandle::isAlive)
                .forEach(process -> {
                    if (forcibly) {
                        process.destroyForcibly();
                    } else {
                        process.destroy();
                    }
                });
    }

    private static OutputCapture awaitOutput(Future<OutputCapture> output) {
        try {
            return output.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            output.cancel(true);
        } catch (ExecutionException | TimeoutException exception) {
            output.cancel(true);
        }
        return new OutputCapture("", false);
    }

    private static OutputCapture readOutput(InputStream input, int maxOutputBytes) throws IOException {
        int limit = Math.max(1, maxOutputBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8_192));
        byte[] buffer = new byte[4_096];
        boolean truncated = false;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int remaining = limit - output.size();
            if (remaining > 0) {
                int copied = Math.min(remaining, read);
                output.write(buffer, 0, copied);
                truncated |= copied < read;
            } else {
                truncated = true;
            }
        }
        return new OutputCapture(output.toString(StandardCharsets.UTF_8), truncated);
    }

    @PreDestroy
    void shutdown() {
        outputReaders.shutdownNow();
    }

    private record OutputCapture(String value, boolean truncated) {
    }
}
