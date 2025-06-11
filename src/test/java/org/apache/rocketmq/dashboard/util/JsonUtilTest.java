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
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    static class TestObj {
        public String name;
        public int value;

        public TestObj() {}
        public TestObj(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestObj)) return false;
            TestObj testObj = (TestObj) o;
            return value == testObj.value && name.equals(testObj.name);
        }
    }

    @Test
    void writeValue() {
        TestObj obj = new TestObj("foo", 42);
        StringWriter writer = new StringWriter();
        JsonUtil.writeValue(writer, obj);
        String json = writer.toString();
        assertTrue(json.contains("\"name\":\"foo\""));
        assertTrue(json.contains("\"value\":42"));
    }

    @Test
    void objectToString_and_stringToObject() {
        TestObj obj = new TestObj("bar", 7);
        String json = JsonUtil.objectToString(obj);
        assertNotNull(json);
        TestObj result = JsonUtil.stringToObject(json, TestObj.class);
        assertEquals(obj, result);
    }

    @Test
    void objectToByte_and_byteToObject() {
        TestObj obj = new TestObj("baz", 99);
        byte[] bytes = JsonUtil.objectToByte(obj);
        assertNotNull(bytes);
        TestObj result = JsonUtil.byteToObject(bytes, TestObj.class);
        assertEquals(obj, result);
    }

    @Test
    void stringToObject_withTypeReference() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        String json = JsonUtil.objectToString(map);
        TypeReference<Map<String, Integer>> typeRef = new TypeReference<>() {};
        Map<String, Integer> result = JsonUtil.stringToObject(json, typeRef);
        assertEquals(map, result);
    }

    @Test
    void byteToObject_withTypeReference() {
        Map<String, String> map = new HashMap<>();
        map.put("x", "y");
        byte[] bytes = JsonUtil.objectToByte(map);
        TypeReference<Map<String, String>> typeRef = new TypeReference<>() {};
        Map<String, String> result = JsonUtil.byteToObject(bytes, typeRef);
        assertEquals(map, result);
    }

    @Test
    void mapToObj() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "hello");
        map.put("value", "123");
        TestObj obj = JsonUtil.mapToObj(map, TestObj.class);
        assertEquals("hello", obj.name);
        assertEquals(123, obj.value);
    }

    @Test
    void nullAndEdgeCases() {
        assertNull(JsonUtil.objectToString(null));
        assertNull(JsonUtil.objectToByte(null));
        assertNull(JsonUtil.stringToObject(null, TestObj.class));
        assertNull(JsonUtil.stringToObject("", TestObj.class));
        assertNull(JsonUtil.byteToObject(null, TestObj.class));
        assertNull(JsonUtil.stringToObject(null, (TypeReference<?>) null));
        assertNull(JsonUtil.byteToObject(null, (TypeReference<?>) null));
        assertNull(JsonUtil.mapToObj(null, TestObj.class));
    }
}