/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain the copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.cli.schema;

import org.junit.Assert;
import org.junit.Test;

public class ParamSchemaTest {

    @Test
    public void testBuilder() {
        ParamSchema param = ParamSchema.builder()
                .name("cluster")
                .type("string")
                .required(true)
                .description("Target cluster name")
                .defaultValue("default")
                .allowedValues(new String[]{"prod", "staging", "dev"})
                .build();

        Assert.assertNotNull(param);
        Assert.assertEquals("cluster", param.getName());
        Assert.assertEquals("string", param.getType());
        Assert.assertTrue(param.isRequired());
        Assert.assertEquals("Target cluster name", param.getDescription());
        Assert.assertEquals("default", param.getDefaultValue());
        Assert.assertNotNull(param.getAllowedValues());
        Assert.assertEquals(3, param.getAllowedValues().length);
        Assert.assertEquals("prod", param.getAllowedValues()[0]);
    }

    @Test
    public void testBuilderMinimal() {
        ParamSchema param = ParamSchema.builder()
                .name("topicName")
                .type("string")
                .required(true)
                .description("Topic name")
                .build();

        Assert.assertEquals("topicName", param.getName());
        Assert.assertEquals("string", param.getType());
        Assert.assertTrue(param.isRequired());
        Assert.assertEquals("Topic name", param.getDescription());
        Assert.assertNull(param.getDefaultValue());
        Assert.assertNull(param.getAllowedValues());
    }

    @Test
    public void testNoArgsConstructor() {
        ParamSchema param = new ParamSchema();
        Assert.assertNull(param.getName());
        Assert.assertNull(param.getType());
        Assert.assertFalse(param.isRequired());
        Assert.assertNull(param.getDescription());
        Assert.assertNull(param.getDefaultValue());
        Assert.assertNull(param.getAllowedValues());
    }

    @Test
    public void testAllArgsConstructor() {
        String[] allowed = {"a", "b"};
        ParamSchema param = new ParamSchema(
                "testParam", "integer", true, "A test param", "42", allowed);

        Assert.assertEquals("testParam", param.getName());
        Assert.assertEquals("integer", param.getType());
        Assert.assertTrue(param.isRequired());
        Assert.assertEquals("A test param", param.getDescription());
        Assert.assertEquals("42", param.getDefaultValue());
        Assert.assertArrayEquals(allowed, param.getAllowedValues());
    }

    @Test
    public void testSetters() {
        ParamSchema param = new ParamSchema();
        param.setName("perm");
        param.setType("string");
        param.setRequired(false);
        param.setDescription("Permission setting");
        param.setDefaultValue("PUB|SUB");
        param.setAllowedValues(new String[]{"PUB", "SUB", "PUB|SUB"});

        Assert.assertEquals("perm", param.getName());
        Assert.assertEquals("string", param.getType());
        Assert.assertFalse(param.isRequired());
        Assert.assertEquals("Permission setting", param.getDescription());
        Assert.assertEquals("PUB|SUB", param.getDefaultValue());
        Assert.assertEquals(3, param.getAllowedValues().length);
    }

    @Test
    public void testRequiredFalse() {
        ParamSchema param = ParamSchema.builder()
                .name("optional")
                .type("string")
                .required(false)
                .description("Optional param")
                .build();

        Assert.assertFalse(param.isRequired());
    }

    @Test
    public void testEqualsAndHashCode() {
        ParamSchema p1 = ParamSchema.builder()
                .name("cluster")
                .type("string")
                .required(true)
                .description("Cluster")
                .build();

        ParamSchema p2 = ParamSchema.builder()
                .name("cluster")
                .type("string")
                .required(true)
                .description("Cluster")
                .build();

        Assert.assertEquals(p1, p2);
        Assert.assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    public void testToString() {
        ParamSchema param = ParamSchema.builder()
                .name("cluster")
                .type("string")
                .required(true)
                .description("Cluster name")
                .build();

        String str = param.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("cluster"));
        Assert.assertTrue(str.contains("string"));
    }

    @Test
    public void testAllowedValuesEmpty() {
        ParamSchema param = ParamSchema.builder()
                .name("test")
                .type("string")
                .required(true)
                .description("Test")
                .allowedValues(new String[]{})
                .build();

        Assert.assertNotNull(param.getAllowedValues());
        Assert.assertEquals(0, param.getAllowedValues().length);
    }

    @Test
    public void testDefaultValueNull() {
        ParamSchema param = ParamSchema.builder()
                .name("test")
                .type("string")
                .required(true)
                .description("Test")
                .build();

        Assert.assertNull(param.getDefaultValue());
    }
}