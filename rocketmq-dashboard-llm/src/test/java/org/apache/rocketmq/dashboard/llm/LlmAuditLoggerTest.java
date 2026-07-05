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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LlmAuditLoggerTest {

    private static final String TEST_USER_HOME;

    static {
        String tmpDir = System.getProperty("java.io.tmpdir");
        TEST_USER_HOME = tmpDir + File.separator + "llm-audit-test-" + System.currentTimeMillis();
    }

    @Before
    public void setUp() throws IOException {
        // Point user.home to temporary directory
        System.setProperty("user.home", TEST_USER_HOME);

        // Clean up any previous test files
        File testHome = new File(TEST_USER_HOME);
        if (testHome.exists()) {
            Files.walk(testHome.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        testHome.mkdirs();

        // Reset the dirCreated flag via reflection to avoid stale state
        resetDirCreatedFlag();
    }

    @After
    public void tearDown() throws IOException {
        File testHome = new File(TEST_USER_HOME);
        if (testHome.exists()) {
            Files.walk(testHome.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // ---- Basic logging tests ---------------------------------------------------

    @Test
    public void testLogWritesAuditFile() throws IOException {
        LlmAuditLogger.log("test-cluster", "admin", "rmq.topic.list",
                "{\"cluster\":\"test\"}", "success", "console-llm");

        // Check that audit directory was created
        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        assertTrue("Audit directory should exist", auditDir.exists());
        assertTrue("Audit directory should be a directory", auditDir.isDirectory());
    }

    @Test
    public void testLogContainsAllFields() throws IOException {
        LlmAuditLogger.log("test-cluster", "admin", "rmq.topic.list",
                "{\"cluster\":\"test\"}", "success", "console-llm");

        // Find the audit log file
        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-") && name.endsWith(".log"));
        assertNotNull("Should find at least one log file", logFiles);
        assertTrue("Should have at least one log file", logFiles.length >= 1);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Log should contain cluster", content.contains("cluster=test-cluster"));
        assertTrue("Log should contain user", content.contains("user=admin"));
        assertTrue("Log should contain tool", content.contains("tool=rmq.topic.list"));
        assertTrue("Log should contain params", content.contains("params={\"cluster\":\"test\"}"));
        assertTrue("Log should contain result", content.contains("result=success"));
        assertTrue("Log should contain source", content.contains("source=console-llm"));
    }

    @Test
    public void testLogSourceConsoleLlmMarker() throws IOException {
        LlmAuditLogger.log("production-cluster", "operator", "rmq.topic.create",
                "{\"topic\":\"new-topic\"}", "dry_run", "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Source should be console-llm", content.contains("source=console-llm"));
    }

    // ---- Directory creation tests ----------------------------------------------

    @Test
    public void testLogCreatesAuditDirectory() throws IOException {
        // The audit directory should not exist yet
        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        Files.deleteIfExists(auditDir.toPath());
        Files.deleteIfExists(new File(TEST_USER_HOME, ".rmqctl").toPath());

        assertFalse("Audit dir should not exist before logging", auditDir.exists());

        LlmAuditLogger.log("cluster1", "user1", "rmq.topic.list",
                "{}", "success", "console-llm");

        assertTrue("Audit directory should be created by logging", auditDir.exists());
    }

    @Test
    public void testMultipleLogsWriteToSameFile() throws IOException {
        LlmAuditLogger.log("cluster-a", "user-a", "rmq.topic.list",
                "{}", "success", "console-llm");
        LlmAuditLogger.log("cluster-b", "user-b", "rmq.group.list",
                "{}", "success", "console-llm");
        LlmAuditLogger.log("cluster-c", "user-c", "rmq.broker.list",
                "{}", "success", "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        // All three logs should go to the same file (same day)
        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        long count = content.lines().count();
        assertEquals("Should have 3 log entries", 3, count);
    }

    // ---- Null safety tests -----------------------------------------------------

    @Test
    public void testLogWithNullCluster() throws IOException {
        LlmAuditLogger.log(null, "admin", "rmq.topic.list",
                "{}", "success", "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain cluster=-", content.contains("cluster=-"));
    }

    @Test
    public void testLogWithNullUsername() throws IOException {
        LlmAuditLogger.log("cluster", null, "rmq.topic.list",
                "{}", "success", "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain user=-", content.contains("user=-"));
    }

    @Test
    public void testLogWithNullToolName() throws IOException {
        LlmAuditLogger.log("cluster", "admin", null,
                "{}", "success", "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain tool=-", content.contains("tool=-"));
    }

    @Test
    public void testLogWithNullParams() throws IOException {
        LlmAuditLogger.log("cluster", "admin", "rmq.topic.list",
                null, "success", "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain params=-", content.contains("params=-"));
    }

    @Test
    public void testLogWithNullResult() throws IOException {
        LlmAuditLogger.log("cluster", "admin", "rmq.topic.list",
                "{}", null, "console-llm");

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain result=-", content.contains("result=-"));
    }

    @Test
    public void testLogWithNullSource() throws IOException {
        LlmAuditLogger.log("cluster", "admin", "rmq.topic.list",
                "{}", "success", null);

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain source=-", content.contains("source=-"));
    }

    @Test
    public void testLogWithAllNulls() throws IOException {
        LlmAuditLogger.log(null, null, null, null, null, null);

        File auditDir = new File(TEST_USER_HOME, ".rmqctl/audit");
        File[] logFiles = auditDir.listFiles((dir, name) -> name.startsWith("audit-"));
        assertNotNull("Should find log files even with all nulls", logFiles);

        String content = new String(Files.readAllBytes(logFiles[0].toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain cluster=-", content.contains("cluster=-"));
        assertTrue("Should contain user=-", content.contains("user=-"));
        assertTrue("Should contain tool=-", content.contains("tool=-"));
        assertTrue("Should contain params=-", content.contains("params=-"));
        assertTrue("Should contain result=-", content.contains("result=-"));
        assertTrue("Should contain source=-", content.contains("source=-"));
    }

    // ---- Multiple calls and concurrency tests ----------------------------------

    @Test
    public void testLogThrowsNoException() {
        // Should not throw any exceptions
        for (int i = 0; i < 20; i++) {
            LlmAuditLogger.log("cluster-" + i, "user-" + i, "tool-" + i,
                    "{\"param\": " + i + "}", "success-" + i, "console-llm");
        }
    }

    // ---- Helper methods --------------------------------------------------------

    private void resetDirCreatedFlag() {
        try {
            java.lang.reflect.Field field = LlmAuditLogger.class.getDeclaredField("dirCreated");
            field.setAccessible(true);
            field.set(null, false);
        } catch (Exception e) {
            // Ignore reflection errors in test setup
        }
    }
}
