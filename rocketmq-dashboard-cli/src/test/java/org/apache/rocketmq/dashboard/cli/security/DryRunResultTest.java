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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class DryRunResultTest {

    @Test
    public void testBuilder() {
        DryRunResult result = DryRunResult.builder()
                .operation("create topic test")
                .willExecute(true)
                .affectedResources(Arrays.asList("Topic: test"))
                .estimatedDuration("< 1 second")
                .warnings(Arrays.asList("Warning 1", "Warning 2"))
                .build();

        Assert.assertNotNull(result);
        Assert.assertEquals("create topic test", result.getOperation());
        Assert.assertTrue(result.isWillExecute());
        Assert.assertNotNull(result.getAffectedResources());
        Assert.assertEquals(1, result.getAffectedResources().size());
        Assert.assertEquals("< 1 second", result.getEstimatedDuration());
        Assert.assertNotNull(result.getWarnings());
        Assert.assertEquals(2, result.getWarnings().size());
    }

    @Test
    public void testToDisplay() {
        DryRunResult result = DryRunResult.builder()
                .operation("create topic test")
                .willExecute(true)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("DRY RUN PREVIEW"));
        Assert.assertTrue(display.contains("create topic test"));
        Assert.assertTrue(display.contains("YES"));
    }

    @Test
    public void testToDisplayWithWarnings() {
        DryRunResult result = DryRunResult.builder()
                .operation("update topic")
                .willExecute(true)
                .warnings(Arrays.asList("This is irreversible", "Proceed with caution"))
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("WARNINGS"));
        Assert.assertTrue(display.contains("This is irreversible"));
        Assert.assertTrue(display.contains("Proceed with caution"));
    }

    @Test
    public void testToDisplayWithChanges() {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("topic", "test-topic");
        changes.put("readQueueNums", 8);

        DryRunResult result = DryRunResult.builder()
                .operation("create topic")
                .willExecute(true)
                .changeDetails(changes)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("Change Details"));
        Assert.assertTrue(display.contains("test-topic"));
    }

    @Test
    public void testToDisplayWithResources() {
        DryRunResult result = DryRunResult.builder()
                .operation("delete topic")
                .willExecute(false)
                .affectedResources(Arrays.asList("Topic: test1", "Topic: test2", "Consumer Group: g1"))
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("Affected Resources"));
        Assert.assertTrue(display.contains("test1"));
        Assert.assertTrue(display.contains("test2"));
        Assert.assertTrue(display.contains("g1"));
        Assert.assertTrue(display.contains("NO"));
    }

    @Test
    public void testToDisplayWithDuration() {
        DryRunResult result = DryRunResult.builder()
                .operation("query metrics")
                .willExecute(true)
                .estimatedDuration("< 5 seconds")
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("Estimated Duration"));
        Assert.assertTrue(display.contains("< 5 seconds"));
    }

    @Test
    public void testToDisplayNullOperation() {
        DryRunResult result = DryRunResult.builder()
                .willExecute(true)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("unknown"));
    }

    @Test
    public void testToDisplayEmptyResources() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .affectedResources(Arrays.asList())
                .changeDetails(new LinkedHashMap<>())
                .warnings(Arrays.asList())
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        // Empty lists should not show sections
        Assert.assertFalse(display.contains("Affected Resources"));
        Assert.assertFalse(display.contains("Change Details"));
        Assert.assertFalse(display.contains("WARNINGS"));
    }

    @Test
    public void testOperationAndWillExecute() {
        DryRunResult result = DryRunResult.builder()
                .operation("test operation")
                .willExecute(false)
                .build();

        Assert.assertEquals("test operation", result.getOperation());
        Assert.assertFalse(result.isWillExecute());
    }

    @Test
    public void testAffectedResourcesList() {
        List<String> resources = Arrays.asList("res1", "res2", "res3");
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .affectedResources(resources)
                .build();

        Assert.assertEquals(3, result.getAffectedResources().size());
        Assert.assertEquals("res1", result.getAffectedResources().get(0));
        Assert.assertEquals("res2", result.getAffectedResources().get(1));
        Assert.assertEquals("res3", result.getAffectedResources().get(2));
    }
}
