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
package org.apache.rocketmq.dashboard.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class JsonUtilTest {

    @Test
    public void testString2ObjPreservesMultilineString() {
        String source = "first line\nsecond line\r\nthird line";

        Assert.assertEquals(source, JsonUtil.string2Obj(source, String.class));
    }

    @Test
    public void testString2ObjParsesPrettyJson() {
        String source = "{\n  \"name\": \"rocketmq\",\n  \"enabled\": true\n}";

        Map<String, Object> result = JsonUtil.string2Obj(source, new TypeReference<Map<String, Object>>() {
        });

        Assert.assertNotNull(result);
        Assert.assertEquals("rocketmq", result.get("name"));
        Assert.assertEquals(Boolean.TRUE, result.get("enabled"));
    }
}
