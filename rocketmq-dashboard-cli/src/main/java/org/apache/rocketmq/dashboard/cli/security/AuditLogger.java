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
package org.apache.rocketmq.dashboard.cli.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Thread-safe singleton that logs all write operations to ~/.rmqctl/audit/ with timestamp, cluster, command, result, and user for audit trail. */
public class AuditLogger {

    private static final AuditLogger INSTANCE = new AuditLogger();
    private static final Path AUDIT_DIR = Paths.get(System.getProperty("user.home"), ".rmqctl", "audit");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private volatile boolean dirCreated = false;

    private AuditLogger() {
    }

    public static AuditLogger getInstance() {
        return INSTANCE;
    }

    public void log(String cluster, String command, String result, String user) {
        try {
            ensureAuditDir();

            String today = LocalDate.now().format(DATE_FMT);
            Path logFile = AUDIT_DIR.resolve("audit-" + today + ".log");

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String entry = String.format("[%s] cluster=%s command=%s result=%s user=%s%n",
                    timestamp,
                    cluster != null ? cluster : "-",
                    command != null ? command : "-",
                    result != null ? result : "-",
                    user != null ? user : "-");

            Files.write(logFile, entry.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }

    private void ensureAuditDir() throws IOException {
        if (!dirCreated) {
            synchronized (this) {
                if (!dirCreated) {
                    Files.createDirectories(AUDIT_DIR);
                    dirCreated = true;
                }
            }
        }
    }
}
