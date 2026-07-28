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
package org.apache.rocketmq.dashboard.support;

import org.apache.rocketmq.dashboard.aspect.admin.annotation.OriginalControllerReturnValue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;

@RunWith(MockitoJUnitRunner.Silent.class)
public class GlobalRestfulResponseBodyAdviceTest {

    private final GlobalRestfulResponseBodyAdvice advice = new GlobalRestfulResponseBodyAdvice();

    @SuppressWarnings("unused")
    private static class DummyController {
        @OriginalControllerReturnValue
        Object original() {
            return null;
        }

        Object wrapped() {
            return null;
        }
    }

    @Test
    public void testSupports() throws Exception {
        MethodParameter returnType = new MethodParameter(
                DummyController.class.getDeclaredMethod("wrapped"), -1);
        Assert.assertTrue(advice.supports(returnType, null));
    }

    @Test
    public void testBeforeBodyWriteWithOriginalReturnValue() throws Exception {
        MethodParameter returnType = new MethodParameter(
                DummyController.class.getDeclaredMethod("original"), -1);
        Object body = "raw-body";
        Object result = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON,
                null, null, null);
        Assert.assertSame(body, result);
    }

    @Test
    public void testBeforeBodyWriteWrapsPlainObject() throws Exception {
        MethodParameter returnType = new MethodParameter(
                DummyController.class.getDeclaredMethod("wrapped"), -1);
        Object result = advice.beforeBodyWrite("data", returnType, MediaType.APPLICATION_JSON,
                null, null, null);
        Assert.assertTrue(result instanceof JsonResult);
        Assert.assertEquals("data", ((JsonResult) result).getData());
    }

    @Test
    public void testBeforeBodyWriteKeepsJsonResult() throws Exception {
        MethodParameter returnType = new MethodParameter(
                DummyController.class.getDeclaredMethod("wrapped"), -1);
        JsonResult<String> body = new JsonResult<>("data");
        Object result = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON,
                null, null, null);
        Assert.assertSame(body, result);
    }
}
