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
package org.apache.rocketmq.studio.common.util;

import org.apache.rocketmq.common.message.MessageConst;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared rendering limits for message property maps, used by the message explorer and the DLQ
 * drawer so both apply the same {@value #MAX_PROPERTIES}-entry / {@value #MAX_PROPERTY_VALUE_CHARS}-char
 * caps instead of duplicating the logic. {@link #userProperties} additionally drops the broker-set
 * system keys ({@link MessageConst#STRING_HASH_SET}) so a view labelled "user properties" is not
 * crowded out by system entries once the cap and alphabetical ordering are applied.
 */
public final class MessagePropertyDisplay {

    public static final int MAX_PROPERTIES = 64;
    public static final int MAX_PROPERTY_VALUE_CHARS = 1024;

    private MessagePropertyDisplay() {
    }

    /** Keeps only user-defined properties, dropping broker-set system keys. Null-safe. */
    public static Map<String, String> userProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> user = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (key != null && !MessageConst.STRING_HASH_SET.contains(key)) {
                user.put(key, value);
            }
        });
        return user;
    }

    /** Sorts by key, caps at {@link #MAX_PROPERTIES} entries, and abbreviates each value. Null-safe. */
    public static Map<String, String> limitProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> limited = new LinkedHashMap<>();
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareTo)))
                .limit(MAX_PROPERTIES)
                .forEach(entry -> limited.put(entry.getKey(), abbreviate(entry.getValue())));
        return limited;
    }

    /** True when any value exceeds {@link #MAX_PROPERTY_VALUE_CHARS} and would be abbreviated. */
    public static boolean hasOversizedProperty(Map<String, String> properties) {
        return properties != null && properties.values().stream()
                .anyMatch(value -> value != null && value.length() > MAX_PROPERTY_VALUE_CHARS);
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_PROPERTY_VALUE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_PROPERTY_VALUE_CHARS) + "...";
    }
}
