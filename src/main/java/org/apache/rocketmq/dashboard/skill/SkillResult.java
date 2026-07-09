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
package org.apache.rocketmq.dashboard.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of a Skill execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResult {

    /**
     * Whether the execution was successful
     */
    private boolean success;

    /**
     * Result data (can be a list, object, or primitive)
     */
    private Object data;

    /**
     * Error message if execution failed
     */
    private String errorMessage;

    /**
     * Result type hint for LLM (LIST, OBJECT, TEXT, VOID)
     */
    private String returnType;

    /**
     * Additional metadata about the execution
     */
    private Map<String, Object> metadata;

    /**
     * Create a successful result with data
     */
    public static SkillResult success(Object data, String returnType) {
        return SkillResult.builder()
                .success(true)
                .data(data)
                .returnType(returnType)
                .build();
    }

    /**
     * Create a successful result with a list
     */
    public static SkillResult successList(List<?> data) {
        return SkillResult.builder()
                .success(true)
                .data(data)
                .returnType("LIST")
                .build();
    }

    /**
     * Create a successful result with an object
     */
    public static SkillResult successObject(Object data) {
        return SkillResult.builder()
                .success(true)
                .data(data)
                .returnType("OBJECT")
                .build();
    }

    /**
     * Create a successful result with text
     */
    public static SkillResult successText(String text) {
        return SkillResult.builder()
                .success(true)
                .data(text)
                .returnType("TEXT")
                .build();
    }

    /**
     * Create a successful void result
     */
    public static SkillResult successVoid() {
        return SkillResult.builder()
                .success(true)
                .returnType("VOID")
                .build();
    }

    /**
     * Create a failed result
     */
    public static SkillResult failure(String errorMessage) {
        return SkillResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .returnType("VOID")
                .build();
    }
}
