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

public enum ToolRiskLevel {

    L1("L1_READ_ONLY"),
    L2("L2_MUTATION"),
    L3("L3_HIGH_RISK");

    private final String operationLevel;

    ToolRiskLevel(String operationLevel) {
        this.operationLevel = operationLevel;
    }

    public String code() {
        return name();
    }

    public String operationLevel() {
        return operationLevel;
    }

    public boolean readOnly() {
        return this == L1;
    }

    public boolean requiresConfirmation() {
        return !readOnly();
    }

    public boolean requiresReason() {
        return this == L3;
    }
}
