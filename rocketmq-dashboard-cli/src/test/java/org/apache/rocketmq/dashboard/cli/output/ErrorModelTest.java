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

import org.junit.Assert;
import org.junit.Test;

public class ErrorModelTest {

    @Test
    public void testOf() {
        ErrorModel error = ErrorModel.of("ERR001", "Test error message", "Test hint");
        Assert.assertNotNull(error);
        Assert.assertEquals("ERR001", error.getCode());
        Assert.assertEquals("Test error message", error.getMessage());
        Assert.assertEquals("Test hint", error.getHint());
    }

    @Test
    public void testBuilder() {
        ErrorModel error = ErrorModel.builder()
                .code("ERR002")
                .message("Builder test")
                .hint("Builder hint")
                .build();
        Assert.assertNotNull(error);
        Assert.assertEquals("ERR002", error.getCode());
        Assert.assertEquals("Builder test", error.getMessage());
        Assert.assertEquals("Builder hint", error.getHint());
    }

    @Test
    public void testFields() {
        ErrorModel error = ErrorModel.of("CODE1", "Message text", "Hint text");
        Assert.assertEquals("CODE1", error.getCode());
        Assert.assertEquals("Message text", error.getMessage());
        Assert.assertEquals("Hint text", error.getHint());
    }

    @Test
    public void testNoArgsConstructor() {
        ErrorModel error = new ErrorModel();
        Assert.assertNull(error.getCode());
        Assert.assertNull(error.getMessage());
        Assert.assertNull(error.getHint());
    }

    @Test
    public void testSetters() {
        ErrorModel error = new ErrorModel();
        error.setCode("E100");
        error.setMessage("Test message");
        error.setHint("Test hint");
        Assert.assertEquals("E100", error.getCode());
        Assert.assertEquals("Test message", error.getMessage());
        Assert.assertEquals("Test hint", error.getHint());
    }

    @Test
    public void testEquals() {
        ErrorModel e1 = ErrorModel.of("C1", "M1", "H1");
        ErrorModel e2 = ErrorModel.of("C1", "M1", "H1");
        Assert.assertEquals(e1, e2);
    }

    @Test
    public void testHashCode() {
        ErrorModel e1 = ErrorModel.of("C1", "M1", "H1");
        ErrorModel e2 = ErrorModel.of("C1", "M1", "H1");
        Assert.assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void testToString() {
        ErrorModel error = ErrorModel.of("C1", "M1", "H1");
        String str = error.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("C1"));
        Assert.assertTrue(str.contains("M1"));
        Assert.assertTrue(str.contains("H1"));
    }

    @Test
    public void testErrorModelBuilderNoArgsConstructor() {
        ErrorModel.ErrorModelBuilder builder = ErrorModel.builder();
        ErrorModel error = builder.build();
        Assert.assertNotNull(error);
        Assert.assertNull(error.getCode());
        Assert.assertNull(error.getMessage());
        Assert.assertNull(error.getHint());
    }

    @Test
    public void testErrorModelBuilderPartial() {
        ErrorModel error = ErrorModel.builder()
                .code("ERR_PARTIAL")
                .message("Partial build")
                .build();
        Assert.assertEquals("ERR_PARTIAL", error.getCode());
        Assert.assertEquals("Partial build", error.getMessage());
        Assert.assertNull(error.getHint());
    }

    @Test
    public void testErrorModelBuilderChaining() {
        ErrorModel.ErrorModelBuilder builder = ErrorModel.builder()
                .code("C1")
                .message("M1")
                .hint("H1");
        ErrorModel error = builder.build();
        Assert.assertEquals("C1", error.getCode());
        Assert.assertEquals("M1", error.getMessage());
        Assert.assertEquals("H1", error.getHint());
    }

    @Test
    public void testErrorModelWithNullValues() {
        ErrorModel error = new ErrorModel();
        error.setCode(null);
        error.setMessage(null);
        error.setHint(null);
        Assert.assertNull(error.getCode());
        Assert.assertNull(error.getMessage());
        Assert.assertNull(error.getHint());
    }

    @Test
    public void testErrorModelOfWithEmptyStrings() {
        ErrorModel error = ErrorModel.of("", "", "");
        Assert.assertEquals("", error.getCode());
        Assert.assertEquals("", error.getMessage());
        Assert.assertEquals("", error.getHint());
    }

    @Test
    public void testErrorModelNotEqual() {
        ErrorModel e1 = ErrorModel.of("C1", "M1", "H1");
        ErrorModel e2 = ErrorModel.of("C2", "M2", "H2");
        Assert.assertNotEquals(e1, e2);
    }

    @Test
    public void testErrorModelCanEqual() {
        ErrorModel e1 = ErrorModel.of("C1", "M1", "H1");
        Object obj = new Object();
        Assert.assertNotEquals(e1, obj);
    }
}
