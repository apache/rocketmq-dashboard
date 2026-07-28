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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JsonResultTest {

    @Test
    public void testDataConstructor() {
        JsonResult<String> result = new JsonResult<>("payload");
        assertEquals(0, result.getStatus());
        assertEquals("payload", result.getData());
        assertNull(result.getErrMsg());
    }

    @Test
    public void testStatusAndErrMsgConstructor() {
        JsonResult<Object> result = new JsonResult<>(-1, "some error");
        assertEquals(-1, result.getStatus());
        assertNull(result.getData());
        assertEquals("some error", result.getErrMsg());
    }

    @Test
    public void testFullConstructor() {
        JsonResult<Integer> result = new JsonResult<>(1, 42, "warn");
        assertEquals(1, result.getStatus());
        assertEquals(Integer.valueOf(42), result.getData());
        assertEquals("warn", result.getErrMsg());
    }

    @Test
    public void testSetters() {
        JsonResult<String> result = new JsonResult<>("initial");
        result.setStatus(2);
        result.setData("updated");
        result.setErrMsg("errMsg");
        assertEquals(2, result.getStatus());
        assertEquals("updated", result.getData());
        assertEquals("errMsg", result.getErrMsg());
    }
}
