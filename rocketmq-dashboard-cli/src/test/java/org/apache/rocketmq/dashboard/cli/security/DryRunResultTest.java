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

    @Test
    public void testToDisplayNullAffectedResources() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .affectedResources(null)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertFalse(display.contains("Affected Resources"));
    }

    @Test
    public void testToDisplayNullChangeDetails() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .changeDetails(null)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertFalse(display.contains("Change Details"));
    }

    @Test
    public void testToDisplayNullWarnings() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .warnings(null)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertFalse(display.contains("WARNINGS"));
    }

    @Test
    public void testToDisplayNullEstimatedDuration() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .estimatedDuration(null)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertFalse(display.contains("Estimated Duration"));
    }

    @Test
    public void testToDisplayAllNull() {
        DryRunResult result = DryRunResult.builder()
                .willExecute(false)
                .build();

        String display = result.toDisplay();
        Assert.assertNotNull(display);
        Assert.assertTrue(display.contains("DRY RUN PREVIEW"));
        Assert.assertTrue(display.contains("unknown"));
        Assert.assertTrue(display.contains("NO"));
        Assert.assertFalse(display.contains("Affected Resources"));
        Assert.assertFalse(display.contains("Change Details"));
        Assert.assertFalse(display.contains("WARNINGS"));
        Assert.assertFalse(display.contains("Estimated Duration"));
    }

    @Test
    public void testToDisplayNumberedResources() {
        DryRunResult result = DryRunResult.builder()
                .operation("delete topic")
                .willExecute(true)
                .affectedResources(Arrays.asList("Topic: t1", "Topic: t2", "Topic: t3"))
                .build();

        String display = result.toDisplay();
        Assert.assertTrue(display.contains("1. Topic: t1"));
        Assert.assertTrue(display.contains("2. Topic: t2"));
        Assert.assertTrue(display.contains("3. Topic: t3"));
    }

    @Test
    public void testToDisplayChangeDetailsValues() {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("topic", "my-topic");
        changes.put("readQueueNums", 16);
        changes.put("writeQueueNums", 16);

        DryRunResult result = DryRunResult.builder()
                .operation("create topic")
                .willExecute(true)
                .changeDetails(changes)
                .build();

        String display = result.toDisplay();
        Assert.assertTrue(display.contains("topic: my-topic"));
        Assert.assertTrue(display.contains("readQueueNums: 16"));
        Assert.assertTrue(display.contains("writeQueueNums: 16"));
    }

    @Test
    public void testToDisplayWarningFormat() {
        DryRunResult result = DryRunResult.builder()
                .operation("reset offset")
                .willExecute(true)
                .warnings(Arrays.asList("Data loss possible"))
                .build();

        String display = result.toDisplay();
        Assert.assertTrue(display.contains("! Data loss possible"));
    }

    @Test
    public void testBuilderDefaults() {
        DryRunResult result = DryRunResult.builder().build();
        Assert.assertNull(result.getOperation());
        Assert.assertFalse(result.isWillExecute());
        Assert.assertNull(result.getAffectedResources());
        Assert.assertNull(result.getChangeDetails());
        Assert.assertNull(result.getEstimatedDuration());
        Assert.assertNull(result.getWarnings());
    }

    @Test
    public void testSetters() {
        DryRunResult result = DryRunResult.builder().build();
        result.setOperation("test-op");
        result.setWillExecute(true);
        result.setAffectedResources(Arrays.asList("res1"));
        result.setEstimatedDuration("1s");
        result.setWarnings(Arrays.asList("warn1"));
        result.setChangeDetails(new LinkedHashMap<>());

        Assert.assertEquals("test-op", result.getOperation());
        Assert.assertTrue(result.isWillExecute());
        Assert.assertEquals(1, result.getAffectedResources().size());
        Assert.assertEquals("1s", result.getEstimatedDuration());
        Assert.assertEquals(1, result.getWarnings().size());
    }

    @Test
    public void testToDisplayWillExecuteNo() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(false)
                .build();

        String display = result.toDisplay();
        Assert.assertTrue(display.contains("Will Execute: NO"));
    }

    @Test
    public void testToDisplayWillExecuteYes() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .build();

        String display = result.toDisplay();
        Assert.assertTrue(display.contains("Will Execute: YES"));
    }

    @Test
    public void testToDisplaySeparatorLines() {
        DryRunResult result = DryRunResult.builder()
                .operation("test")
                .willExecute(true)
                .build();

        String display = result.toDisplay();
        // Two separator lines: one at top, one at bottom
        long count = display.lines().filter(line -> line.contains("═══")).count();
        Assert.assertTrue(count >= 2);
    }

    @Test
    public void testEquals() {
        DryRunResult r1 = DryRunResult.builder()
                .operation("op1").willExecute(true)
                .affectedResources(Arrays.asList("res1"))
                .changeDetails(new LinkedHashMap<>())
                .estimatedDuration("1s")
                .warnings(Arrays.asList("w1"))
                .build();
        DryRunResult r2 = DryRunResult.builder()
                .operation("op1").willExecute(true)
                .affectedResources(Arrays.asList("res1"))
                .changeDetails(new LinkedHashMap<>())
                .estimatedDuration("1s")
                .warnings(Arrays.asList("w1"))
                .build();
        Assert.assertEquals(r1, r2);
        Assert.assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testNotEqual() {
        DryRunResult r1 = DryRunResult.builder().operation("op1").willExecute(true).build();
        DryRunResult r2 = DryRunResult.builder().operation("op2").willExecute(true).build();
        Assert.assertNotEquals(r1, r2);
    }

    @Test
    public void testNotEqualWillExecute() {
        DryRunResult r1 = DryRunResult.builder().operation("op1").willExecute(true).build();
        DryRunResult r2 = DryRunResult.builder().operation("op1").willExecute(false).build();
        Assert.assertNotEquals(r1, r2);
    }

    @Test
    public void testNotEqualAffectedResources() {
        DryRunResult r1 = DryRunResult.builder().affectedResources(Arrays.asList("a")).build();
        DryRunResult r2 = DryRunResult.builder().affectedResources(Arrays.asList("b")).build();
        Assert.assertNotEquals(r1, r2);
    }

    @Test
    public void testNotEqualEstimatedDuration() {
        DryRunResult r1 = DryRunResult.builder().estimatedDuration("1s").build();
        DryRunResult r2 = DryRunResult.builder().estimatedDuration("2s").build();
        Assert.assertNotEquals(r1, r2);
    }

    @Test
    public void testNotEqualWarnings() {
        DryRunResult r1 = DryRunResult.builder().warnings(Arrays.asList("w1")).build();
        DryRunResult r2 = DryRunResult.builder().warnings(Arrays.asList("w2")).build();
        Assert.assertNotEquals(r1, r2);
    }

    @Test
    public void testNotEqualChangeDetails() {
        Map<String, Object> c1 = new LinkedHashMap<>(); c1.put("k", "v1");
        Map<String, Object> c2 = new LinkedHashMap<>(); c2.put("k", "v2");
        DryRunResult r1 = DryRunResult.builder().changeDetails(c1).build();
        DryRunResult r2 = DryRunResult.builder().changeDetails(c2).build();
        Assert.assertNotEquals(r1, r2);
    }

    @Test
    public void testEqualsNull() {
        DryRunResult r = DryRunResult.builder().operation("op").build();
        Assert.assertNotEquals(r, null);
    }

    @Test
    public void testEqualsDifferentType() {
        DryRunResult r = DryRunResult.builder().operation("op").build();
        Assert.assertNotEquals(r, "string");
    }

    @Test
    public void testEqualsSameInstance() {
        DryRunResult r = DryRunResult.builder().operation("op").build();
        Assert.assertEquals(r, r);
    }

    @Test
    public void testToString() {
        DryRunResult r = DryRunResult.builder()
                .operation("test-op").willExecute(true)
                .affectedResources(Arrays.asList("res1"))
                .estimatedDuration("1s")
                .warnings(Arrays.asList("warn1"))
                .build();
        String str = r.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("test-op"));
        Assert.assertTrue(str.contains("true"));
    }

    @Test
    public void testCanEqual() {
        DryRunResult r = DryRunResult.builder().operation("op").build();
        // canEqual is called within equals for subclass check
        Assert.assertEquals(r, DryRunResult.builder().operation("op").build());
    }

    @Test
    public void testHashCodeConsistency() {
        DryRunResult r = DryRunResult.builder().operation("op").willExecute(false).build();
        int h1 = r.hashCode();
        int h2 = r.hashCode();
        Assert.assertEquals(h1, h2);
    }
}
