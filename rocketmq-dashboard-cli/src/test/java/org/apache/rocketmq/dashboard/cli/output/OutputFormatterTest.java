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
package org.apache.rocketmq.dashboard.cli.output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class OutputFormatterTest {

    @Test
    public void testFormatTable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("NAME", "test1");
        row1.put("VALUE", "123");
        rows.add(row1);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("NAME", "test2");
        row2.put("VALUE", "456");
        rows.add(row2);

        String result = OutputFormatter.format(rows, OutputFormatter.Format.TABLE);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("NAME"));
        Assert.assertTrue(result.contains("VALUE"));
        Assert.assertTrue(result.contains("test1"));
        Assert.assertTrue(result.contains("test2"));
        Assert.assertTrue(result.contains("123"));
        Assert.assertTrue(result.contains("456"));
        Assert.assertTrue(result.contains("-+-"));
    }

    @Test
    public void testFormatJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        data.put("count", 42);

        String result = OutputFormatter.format(data, OutputFormatter.Format.JSON);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("key"));
        Assert.assertTrue(result.contains("value"));
        Assert.assertTrue(result.contains("42"));
        Assert.assertTrue(result.contains("{"));
        Assert.assertTrue(result.contains("}"));
    }

    @Test
    public void testFormatYaml() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        data.put("count", 42);

        String result = OutputFormatter.format(data, OutputFormatter.Format.YAML);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("key"));
        Assert.assertTrue(result.contains("value"));
        Assert.assertTrue(result.contains("42"));
    }

    @Test
    public void testFormatError() {
        ErrorModel error = ErrorModel.of("ERR001", "Something went wrong", "Try again later");

        String result = OutputFormatter.formatError(error, OutputFormatter.Format.TABLE);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("ERROR"));
        Assert.assertTrue(result.contains("ERR001"));
        Assert.assertTrue(result.contains("Something went wrong"));
        Assert.assertTrue(result.contains("HINT"));
        Assert.assertTrue(result.contains("Try again later"));
    }

    @Test
    public void testFormatErrorJson() {
        ErrorModel error = ErrorModel.of("ERR001", "Something went wrong", "Try again later");

        String result = OutputFormatter.formatError(error, OutputFormatter.Format.JSON);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("ERR001"));
        Assert.assertTrue(result.contains("Something went wrong"));
        Assert.assertTrue(result.contains("Try again later"));
        Assert.assertTrue(result.contains("{"));
        Assert.assertTrue(result.contains("}"));
    }

    @Test
    public void testFormatErrorYaml() {
        ErrorModel error = ErrorModel.of("ERR001", "Something went wrong", "Try again later");

        String result = OutputFormatter.formatError(error, OutputFormatter.Format.YAML);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("ERR001"));
        Assert.assertTrue(result.contains("Something went wrong"));
        Assert.assertTrue(result.contains("Try again later"));
    }

    @Test
    public void testFormatEmptyList() {
        List<String> empty = new ArrayList<>();
        String result = OutputFormatter.format(empty, OutputFormatter.Format.TABLE);
        Assert.assertEquals("(empty)", result.trim());
    }

    @Test
    public void testFormatNull() {
        String result = OutputFormatter.format(null, OutputFormatter.Format.TABLE);
        Assert.assertEquals("", result);
    }

    @Test
    public void testFormatSimpleList() {
        List<String> items = Arrays.asList("alpha", "beta", "gamma");
        String result = OutputFormatter.format(items, OutputFormatter.Format.TABLE);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("alpha"));
        Assert.assertTrue(result.contains("beta"));
        Assert.assertTrue(result.contains("gamma"));
        // Should be numbered list
        Assert.assertTrue(result.contains("1."));
        Assert.assertTrue(result.contains("2."));
        Assert.assertTrue(result.contains("3."));
    }

    @Test
    public void testFormatMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Name", "test");
        map.put("Status", "OK");

        String result = OutputFormatter.format(map, OutputFormatter.Format.TABLE);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("Name"));
        Assert.assertTrue(result.contains("test"));
        Assert.assertTrue(result.contains("Status"));
        Assert.assertTrue(result.contains("OK"));
    }

    @Test
    public void testFormatJsonList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", "value");
        rows.add(row);

        String result = OutputFormatter.format(rows, OutputFormatter.Format.JSON);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("["));
        Assert.assertTrue(result.contains("]"));
        Assert.assertTrue(result.contains("key"));
        Assert.assertTrue(result.contains("value"));
    }

    @Test
    public void testFormatYamlList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", "value");
        rows.add(row);

        String result = OutputFormatter.format(rows, OutputFormatter.Format.YAML);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("key"));
        Assert.assertTrue(result.contains("value"));
    }

    @Test
    public void testFormatSimpleValue() {
        String result = OutputFormatter.format("hello", OutputFormatter.Format.TABLE);
        Assert.assertEquals("hello", result);
    }

    @Test
    public void testFormatIntegerValue() {
        String result = OutputFormatter.format(42, OutputFormatter.Format.TABLE);
        Assert.assertEquals("42", result);
    }

    @Test
    public void testFormatEmptyMap() {
        Map<String, Object> empty = new LinkedHashMap<>();
        String result = OutputFormatter.format(empty, OutputFormatter.Format.TABLE);
        Assert.assertEquals("(empty)", result.trim());
    }

    @Test
    public void testFormatTableWithNullValues() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("NAME", null);
        row.put("VALUE", "42");
        rows.add(row);

        String result = OutputFormatter.format(rows, OutputFormatter.Format.TABLE);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("42"));
    }

    @Test
    public void testFormatErrorDefault() {
        ErrorModel error = ErrorModel.of("E1", "msg", "hint");
        String result = OutputFormatter.formatError(error, OutputFormatter.Format.TABLE);
        Assert.assertTrue(result.startsWith("ERROR"));
        Assert.assertTrue(result.contains("E1"));
        Assert.assertTrue(result.contains("msg"));
        Assert.assertTrue(result.contains("HINT"));
        Assert.assertTrue(result.contains("hint"));
    }

    @Test
    public void testFormatEmojiInTable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CURRENT", "*");
        row.put("NAME", "test-ctx");
        rows.add(row);

        String result = OutputFormatter.format(rows, OutputFormatter.Format.TABLE);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("*"));
    }
}
