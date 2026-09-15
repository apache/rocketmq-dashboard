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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Configuration-gated lifecycle dispatch using a fixed server-side executable. */
@Component
public class ProcessLifecycleOperationExecutor implements LifecycleOperationExecutor {

    private final LifecycleProperties properties;
    private final LifecycleProcessRunner processRunner;

    @Autowired
    ProcessLifecycleOperationExecutor(LifecycleProperties properties, LifecycleProcessRunner processRunner) {
        this.properties = properties;
        this.processRunner = processRunner;
    }

    @Override
    public LifecycleOperationResult execute(LifecycleOperationRequest request) {
        validateRequest(request);
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getExecutable())) {
            throw new BusinessException(501, "Lifecycle operation executor is not configured");
        }
        Set<LifecycleOperation> allowed = properties.getAllowedOperations();
        if (allowed == null || !allowed.contains(request.operation())) {
            throw new BusinessException(501, "Lifecycle operation is not allowlisted: "
                    + request.operation().name());
        }
        Duration timeout = properties.getTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new BusinessException(500, "Lifecycle operation timeout must be positive");
        }
        if (properties.getMaxOutputBytes() < 1) {
            throw new BusinessException(500, "Lifecycle operation maxOutputBytes must be positive");
        }

        LifecycleProcessResult process;
        try {
            process = processRunner.run(buildCommand(request), properties.getWorkingDirectory(),
                    timeout, properties.getMaxOutputBytes());
        } catch (RuntimeException exception) {
            throw new BusinessException(502, "Failed to start lifecycle operation: "
                    + rootMessage(exception));
        }
        String output = boundedMessage(process.output(), process.outputTruncated(), properties.getMaxOutputBytes());
        if (process.timedOut()) {
            throw new BusinessException(504, "Lifecycle operation timed out: " + output);
        }
        if (process.exitCode() != 0) {
            throw new BusinessException(502, "Lifecycle operation failed with exit code "
                    + process.exitCode() + ": " + output);
        }
        return new LifecycleOperationResult(request.operation(), request.clusterId(), request.target(),
                request.requestId(), true, output.isBlank() ? "Lifecycle operation accepted" : output);
    }

    private List<String> buildCommand(LifecycleOperationRequest request) {
        List<String> command = new ArrayList<>();
        command.add(properties.getExecutable().trim());
        command.add(request.operation().commandName());
        command.add("--cluster-id");
        command.add(request.clusterId());
        command.add("--target");
        command.add(request.target());
        if (StringUtils.hasText(request.targetAddress())) {
            command.add("--target-address");
            command.add(request.targetAddress());
        }
        if (StringUtils.hasText(request.targetVersion())) {
            command.add("--target-version");
            command.add(request.targetVersion());
        }
        command.add("--request-id");
        command.add(request.requestId());
        return List.copyOf(command);
    }

    private static void validateRequest(LifecycleOperationRequest request) {
        if (request == null) {
            throw new BusinessException(400, "Lifecycle operation request is required");
        }
        if (request.operation() == null) {
            throw new BusinessException(400, "Lifecycle operation is required");
        }
        requireText(request.clusterId(), "clusterId");
        requireText(request.target(), "target");
        requireText(request.requestId(), "requestId");
        if (request.operation() == LifecycleOperation.NAMESERVER_UPGRADE) {
            requireText(request.targetVersion(), "targetVersion");
        }
        if (request.operation() == LifecycleOperation.NAMESERVER_UPDATE) {
            requireText(request.targetAddress(), "targetAddress");
        }
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, field + " is required");
        }
    }

    private static String boundedMessage(String output, boolean truncated, int maxOutputBytes) {
        String normalized = output == null ? "" : output.trim();
        if (truncated && !normalized.endsWith("[output truncated]")) {
            normalized += (normalized.isBlank() ? "" : " ") + "[output truncated]";
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length <= maxOutputBytes) {
            return normalized;
        }
        String suffix = " [output truncated]";
        int suffixBytes = suffix.getBytes(StandardCharsets.UTF_8).length;
        if (maxOutputBytes <= suffixBytes) {
            return truncateUtf8(normalized, maxOutputBytes);
        }
        return truncateUtf8(normalized, maxOutputBytes - suffixBytes) + suffix;
    }

    private static String truncateUtf8(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int end = Math.max(0, maxBytes);
        while (end > 0 && (bytes[end] & 0xc0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage() : current.getClass().getSimpleName();
    }
}
