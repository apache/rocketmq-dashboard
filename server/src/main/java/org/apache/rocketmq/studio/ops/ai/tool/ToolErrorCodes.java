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
package org.apache.rocketmq.studio.ops.ai.tool;

public final class ToolErrorCodes {
    public static final String TOOL_NAME_REQUIRED = "TOOL_NAME_REQUIRED";
    public static final String TOOL_NOT_FOUND = "TOOL_NOT_FOUND";
    public static final String TOOL_INPUT_INVALID = "TOOL_INPUT_INVALID";
    public static final String TOOL_HANDLER_NOT_IMPLEMENTED = "TOOL_HANDLER_NOT_IMPLEMENTED";
    public static final String TOOL_CLUSTER_REQUIRED = "TOOL_CLUSTER_REQUIRED";
    public static final String TOOL_CAPABILITY_UNSUPPORTED = "TOOL_CAPABILITY_UNSUPPORTED";
    public static final String TOOL_CONFIRMATION_REQUIRED = "TOOL_CONFIRMATION_REQUIRED";
    public static final String TOOL_REASON_REQUIRED = "TOOL_REASON_REQUIRED";
    public static final String TOOL_OUTPUT_INVALID = "TOOL_OUTPUT_INVALID";
    public static final String TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED";
    public static final String CLUSTER_TYPE_UNAVAILABLE = "CLUSTER_TYPE_UNAVAILABLE";

    private ToolErrorCodes() {
    }
}
