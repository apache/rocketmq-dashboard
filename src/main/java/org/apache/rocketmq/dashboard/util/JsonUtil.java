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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Map;

@SuppressWarnings("unchecked")
@Slf4j
public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String PARSE_STRING_ERROR_MESSAGE = "Parse String to Object error\nString: {}\nClass<T>: {}\nError: {}";

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setFilters(new SimpleFilterProvider().setFailOnUnknownId(false));
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    }

    private JsonUtil() {
        // Prevent instantiation
    }

    public static void writeValue(Writer writer, Object object) {
        try {
            objectMapper.writeValue(writer, object);
        } catch (IOException e) {
            Throwables.propagateIfPossible(e);
        }
    }

    public static <T> String objectToString(T source) {
        if (source == null) {
            return null;
        }

        try {
            return source instanceof String ? (String) source : objectMapper.writeValueAsString(source);
        } catch (Exception e) {
            log.error("Parse Object to String error src={}", source, e);
            return null;
        }
    }

    public static <T> byte[] objectToByte(T source) {
        if (source == null) {
            return null;
        }

        try {
            return source instanceof byte[] ? (byte[]) source : objectMapper.writeValueAsBytes(source);
        } catch (Exception e) {
            log.error("Parse Object to byte[] error", e);
            return null;
        }
    }

    public static <T> T stringToObject(String str, Class<T> clazz) {
        if (Strings.isNullOrEmpty(str) || clazz == null) {
            return null;
        }
        str = escapesSpecialChar(str);
        try {
            return clazz.equals(String.class) ? (T) str : objectMapper.readValue(str, clazz);
        } catch (Exception e) {
            log.error(PARSE_STRING_ERROR_MESSAGE, str, clazz.getName(), e.getMessage());
            return null;
        }
    }

    public static <T> T byteToObject(byte[] bytes, Class<T> clazz) {
        if (bytes == null || clazz == null) {
            return null;
        }
        try {
            return clazz.equals(byte[].class) ? (T) bytes : objectMapper.readValue(bytes, clazz);
        } catch (Exception e) {
            log.error(PARSE_STRING_ERROR_MESSAGE, bytes, clazz.getName(), e.getMessage());
            return null;
        }
    }

    public static <T> T stringToObject(String str, TypeReference<T> typeReference) {
        if (Strings.isNullOrEmpty(str) || typeReference == null) {
            return null;
        }
        str = escapesSpecialChar(str);
        try {
            return (T) (typeReference.getType().equals(String.class) ? str : objectMapper.readValue(str, typeReference));
        } catch (Exception e) {
            log.error(PARSE_STRING_ERROR_MESSAGE, str, typeReference.getType(), e.getMessage());
            return null;
        }
    }

    public static <T> T byteToObject(byte[] bytes, TypeReference<T> typeReference) {
        if (bytes == null || typeReference == null) {
            return null;
        }
        try {
            return (T) (typeReference.getType().equals(byte[].class) ? bytes : objectMapper.readValue(bytes,
                    typeReference));
        } catch (Exception e) {
            log.error(PARSE_STRING_ERROR_MESSAGE, bytes,
                    typeReference.getType(), e.getMessage());
            return null;
        }
    }

    public static <T> T mapToObj(Map<String, String> map, Class<T> clazz) {
        String str = objectToString(map);
        return stringToObject(str, clazz);
    }

    private static String escapesSpecialChar(String str) {
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }
}
