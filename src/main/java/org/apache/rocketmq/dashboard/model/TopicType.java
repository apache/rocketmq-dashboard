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
package org.apache.rocketmq.dashboard.model;

/**
 *
 *
 */
public enum TopicType {

    /**
 *
     */
    NORMAL("NORMAL", "Normal message", false),

    /**
 *
     */
    FIFO("FIFO", "FIFO ordered message", true),

    /**
 *
     */
    DELAY("DELAY", "Delay message", true),

    /**
 *
     */
    TRANSACTION("TRANSACTION", "Transaction message", true),

    /**
 *
     */
    LITE("LITE", "LiteTopic", true);

    private final String value;
    private final String description;
    private final boolean requiresSpecialConfig;

    TopicType(String value, String description, boolean requiresSpecialConfig) {
        this.value = value;
        this.description = description;
        this.requiresSpecialConfig = requiresSpecialConfig;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequiresSpecialConfig() {
        return requiresSpecialConfig;
    }

    /**
 *
     */
    public boolean isV5Specific() {
        return this == LITE;
    }

    /**
 *
     */
    public boolean isNotSupportedInV4() {
        return this == LITE;
    }

    public static TopicType fromValue(String value) {
        for (TopicType type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown topic type: " + value);
    }
}