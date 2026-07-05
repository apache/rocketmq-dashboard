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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.rocketmq.dashboard.cli.AbstractCliTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AuditLoggerTest extends AbstractCliTest {

    @Before
    public void setUp() throws Exception {
        resetConfig();
    }

    @Test
    public void testGetInstance() {
        AuditLogger logger1 = AuditLogger.getInstance();
        AuditLogger logger2 = AuditLogger.getInstance();
        Assert.assertNotNull(logger1);
        Assert.assertSame(logger1, logger2);
    }

    @Test
    public void testLogDoesNotThrow() {
        AuditLogger logger = AuditLogger.getInstance();
        // log() catches IOException internally; should never throw
        logger.log("test-cluster", "topic create", "SUCCESS", "admin");
        // If we get here without exception, the test passes
    }

    @Test
    public void testLogMultipleEntriesDoesNotThrow() {
        AuditLogger logger = AuditLogger.getInstance();
        logger.log("cluster1", "command1", "SUCCESS", "user1");
        logger.log("cluster2", "command2", "FAILED", "user2");
        // Verify no exception thrown
    }

    @Test
    public void testLogWithNullValuesDoesNotThrow() {
        AuditLogger logger = AuditLogger.getInstance();
        logger.log(null, null, null, null);
        // Verify no exception thrown for null inputs
    }
}
