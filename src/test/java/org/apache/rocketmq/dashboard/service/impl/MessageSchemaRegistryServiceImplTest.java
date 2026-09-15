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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.model.MessageSchemaReport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MessageSchemaRegistryServiceImplTest {

    private MessageSchemaRegistryServiceImpl messageSchemaRegistryService;

    @Before
    public void setUp() {
        messageSchemaRegistryService = new MessageSchemaRegistryServiceImpl();
    }

    @Test
    public void testGetSchemaReport() {
        MessageSchemaReport report = messageSchemaRegistryService.getSchemaReport("test-topic");
        Assert.assertNotNull(report);
        Assert.assertEquals("test-topic", report.getTopic());
        Assert.assertEquals("JSON_SCHEMA", report.getSchemaType());
        Assert.assertEquals(2, report.getCurrentVersion());
        Assert.assertNotNull(report.getVersionHistory());
        Assert.assertFalse(report.getVersionHistory().isEmpty());
    }

    @Test
    public void testTestSchemaEvolutionCompatible() {
        String newSchema = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"properties\": {\n" +
            "    \"orderId\": { \"type\": \"string\" },\n" +
            "    \"amount\": { \"type\": \"number\" }\n" +
            "  },\n" +
            "  \"required\": [\"orderId\", \"amount\"]\n" +
            "}";

        MessageSchemaReport report = messageSchemaRegistryService.testSchemaEvolution("test-topic", newSchema, "BACKWARD");
        Assert.assertNotNull(report);
        Assert.assertTrue(report.isEvolutionCompatible());
    }

    @Test
    public void testTestSchemaEvolutionIncompatible() {
        String breakingSchema = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"properties\": {\n" +
            "    \"orderId\": { \"type\": \"string\" },\n" +
            "    \"amount\": { \"type\": \"number\" },\n" +
            "    \"newMandatoryField\": { \"type\": \"string\" }\n" +
            "  },\n" +
            "  \"required\": [\"orderId\", \"amount\", \"newMandatoryField\"]\n" +
            "}";

        MessageSchemaReport report = messageSchemaRegistryService.testSchemaEvolution("test-topic", breakingSchema, "BACKWARD");
        Assert.assertNotNull(report);
        Assert.assertFalse(report.isEvolutionCompatible());
        Assert.assertFalse(report.getCompatibilityDiffs().isEmpty());
    }

    @Test
    public void testValidatePayload() {
        Assert.assertTrue(messageSchemaRegistryService.validatePayload("test-topic", "{\"valid\": true}"));
        Assert.assertFalse(messageSchemaRegistryService.validatePayload("test-topic", "invalid json"));
        Assert.assertFalse(messageSchemaRegistryService.validatePayload("test-topic", ""));
    }
}
