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
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class JsonUtilTest {

    public static class SamplePojo {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class BadPojo {
        public String getBoom() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    public void testWriteValue() {
        StringWriter writer = new StringWriter();
        SamplePojo pojo = new SamplePojo();
        pojo.setName("test");
        pojo.setCount(3);
        JsonUtil.writeValue(writer, pojo);
        assertTrue(writer.toString().contains("\"name\":\"test\""));
        assertTrue(writer.toString().contains("\"count\":3"));
    }

    @Test
    public void testWriteValueIOExceptionSwallowed() {
        Writer failing = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("write failed");
            }

            @Override
            public void flush() throws IOException {
                throw new IOException("flush failed");
            }

            @Override
            public void close() {
            }
        };
        // Checked IOException is not propagated by Throwables.propagateIfPossible
        JsonUtil.writeValue(failing, "data");
    }

    @Test
    public void testObj2String() {
        assertNull(JsonUtil.obj2String(null));
        assertEquals("plain", JsonUtil.obj2String("plain"));

        SamplePojo pojo = new SamplePojo();
        pojo.setName("a");
        pojo.setCount(1);
        String json = JsonUtil.obj2String(pojo);
        assertTrue(json.contains("\"name\":\"a\""));

        assertNull(JsonUtil.obj2String(new BadPojo()));
    }

    @Test
    public void testObj2Byte() {
        assertNull(JsonUtil.obj2Byte(null));
        byte[] raw = new byte[] {1, 2, 3};
        assertSame(raw, JsonUtil.obj2Byte(raw));

        SamplePojo pojo = new SamplePojo();
        pojo.setName("a");
        pojo.setCount(1);
        byte[] bytes = JsonUtil.obj2Byte(pojo);
        assertNotNull(bytes);
        assertTrue(new String(bytes).contains("\"name\":\"a\""));

        assertNull(JsonUtil.obj2Byte(new BadPojo()));
    }

    @Test
    public void testString2ObjWithClass() {
        assertNull(JsonUtil.string2Obj(null, SamplePojo.class));
        assertNull(JsonUtil.string2Obj("", SamplePojo.class));
        assertNull(JsonUtil.string2Obj("{}", (Class<SamplePojo>) null));

        assertEquals("raw", JsonUtil.string2Obj("raw", String.class));

        SamplePojo pojo = JsonUtil.string2Obj("{\"name\":\"a\",\"count\":2}", SamplePojo.class);
        assertNotNull(pojo);
        assertEquals("a", pojo.getName());
        assertEquals(2, pojo.getCount());

        // Special characters are escaped before parsing
        SamplePojo escaped = JsonUtil.string2Obj("{\"name\":\"line1\nline2\r\",\"count\":1}", SamplePojo.class);
        assertNotNull(escaped);
        assertEquals("line1\nline2\r", escaped.getName());

        assertNull(JsonUtil.string2Obj("not-json", SamplePojo.class));
    }

    @Test
    public void testString2ObjWithTypeReference() {
        TypeReference<Map<String, String>> mapType = new TypeReference<Map<String, String>>() {
        };
        assertNull(JsonUtil.string2Obj(null, mapType));
        assertNull(JsonUtil.string2Obj("{}", (TypeReference<Map<String, String>>) null));

        Map<String, String> map = JsonUtil.string2Obj("{\"k\":\"v\"}", mapType);
        assertEquals("v", map.get("k"));

        TypeReference<String> stringType = new TypeReference<String>() {
        };
        assertEquals("raw", JsonUtil.string2Obj("raw", stringType));

        TypeReference<List<SamplePojo>> listType = new TypeReference<List<SamplePojo>>() {
        };
        List<SamplePojo> list = JsonUtil.string2Obj("[{\"name\":\"a\",\"count\":1}]", listType);
        assertEquals(1, list.size());
        assertEquals("a", list.get(0).getName());

        assertNull(JsonUtil.string2Obj("not-json", listType));
    }

    @Test
    public void testByte2ObjWithClass() {
        assertNull(JsonUtil.byte2Obj(null, SamplePojo.class));
        assertNull(JsonUtil.byte2Obj(new byte[0], (Class<SamplePojo>) null));

        byte[] raw = new byte[] {9};
        assertArrayEquals(raw, JsonUtil.byte2Obj(raw, byte[].class));

        SamplePojo pojo = JsonUtil.byte2Obj("{\"name\":\"b\",\"count\":5}".getBytes(), SamplePojo.class);
        assertEquals("b", pojo.getName());
        assertEquals(5, pojo.getCount());

        assertNull(JsonUtil.byte2Obj("bad".getBytes(), SamplePojo.class));
    }

    @Test
    public void testByte2ObjWithTypeReference() {
        TypeReference<Map<String, String>> mapType = new TypeReference<Map<String, String>>() {
        };
        assertNull(JsonUtil.byte2Obj(null, mapType));
        assertNull(JsonUtil.byte2Obj(new byte[0], (TypeReference<Map<String, String>>) null));

        Map<String, String> map = JsonUtil.byte2Obj("{\"k\":\"v\"}".getBytes(), mapType);
        assertEquals("v", map.get("k"));

        TypeReference<byte[]> byteType = new TypeReference<byte[]>() {
        };
        byte[] raw = new byte[] {1};
        assertArrayEquals(raw, JsonUtil.byte2Obj(raw, byteType));

        assertNull(JsonUtil.byte2Obj("bad".getBytes(), mapType));
    }

    @Test
    public void testMap2Obj() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "c");
        map.put("count", "7");
        SamplePojo pojo = JsonUtil.map2Obj(map, SamplePojo.class);
        assertNotNull(pojo);
        assertEquals("c", pojo.getName());
        assertEquals(7, pojo.getCount());
    }
}
