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
package org.apache.rocketmq.dashboard.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audit logger for LLM-initiated tool calls from the console.
 * Logs all operations to ~/.rmqctl/audit/ directory.
 */
public class LlmAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmAuditLogger.class);
    private static final Path AUDIT_DIR =
            Paths.get(System.getProperty("user.home"), ".rmqctl", "audit");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static volatile boolean dirCreated = false;

    /**
     * Log an LLM-initiated tool execution.
     *
     * @param cluster  the target cluster name
     * @param username the user who initiated the action
     * @param toolName the tool that was called
     * @param params   the parameters passed to the tool
     * @param result   the result of the operation
     * @param source   source identifier, typically "console-llm"
     */
    public static void log(String cluster, String username, String toolName,
                           String params, String result, String source) {
        try {
            ensureAuditDir();

            String today = LocalDate.now().format(DATE_FMT);
            Path logFile = AUDIT_DIR.resolve("audit-" + today + ".log");

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String entry = String.format("[%s] cluster=%s user=%s tool=%s params=%s result=%s source=%s%n",
                    timestamp,
                    cluster != null ? cluster : "-",
                    username != null ? username : "-",
                    toolName != null ? toolName : "-",
                    params != null ? params : "-",
                    result != null ? result : "-",
                    source != null ? source : "-");

            Files.write(logFile, entry.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException e) {
            log.error("Failed to write LLM audit log: {}", e.getMessage(), e);
        }
    }

    private static void ensureAuditDir() throws IOException {
        if (!dirCreated) {
            synchronized (LlmAuditLogger.class) {
                if (!dirCreated) {
                    Files.createDirectories(AUDIT_DIR);
                    dirCreated = true;
                }
            }
        }
    }
}
