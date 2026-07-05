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
package org.apache.rocketmq.dashboard.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class McpServerApplicationTest {

    @Test
    public void testMainClassInstantiation() {
        McpServerApplication app = new McpServerApplication();
        assertNotNull("McpServerApplication should be instantiable", app);
    }

    @Test
    public void testHelpFlagPrintsUsage() {
        PrintStream originalOut = System.out;
        try {
            ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
            System.setOut(new PrintStream(capturedOut));

            McpServerApplication.main(new String[]{"--help"});

            String output = capturedOut.toString();
            assertTrue("Should print RocketMQ header", output.contains("RocketMQ"));
            assertTrue("Should print usage section", output.contains("Usage:"));
            assertTrue("Should mention --transport", output.contains("--transport"));
            assertTrue("Should mention --port", output.contains("--port"));
            assertTrue("Should mention --enable-dangerous-ops", output.contains("--enable-dangerous-ops"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testHelpFlagHPrintsUsage() {
        PrintStream originalOut = System.out;
        try {
            ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
            System.setOut(new PrintStream(capturedOut));

            McpServerApplication.main(new String[]{"-h"});

            String output = capturedOut.toString();
            assertTrue("Should print usage for -h flag", output.contains("RocketMQ"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testClassHasSpringBootAnnotation() {
        // Verify the class has @SpringBootApplication annotation
        assertTrue("McpServerApplication should be annotated with @SpringBootApplication",
                McpServerApplication.class.isAnnotationPresent(
                        org.springframework.boot.autoconfigure.SpringBootApplication.class));
    }

    @Test
    public void testClassExtendsObject() {
        assertTrue("McpServerApplication should extend Object",
                Object.class.isAssignableFrom(McpServerApplication.class));
    }

    @Test
    public void testMainMethodExists() throws NoSuchMethodException {
        McpServerApplication.class.getMethod("main", String[].class);
    }
}
