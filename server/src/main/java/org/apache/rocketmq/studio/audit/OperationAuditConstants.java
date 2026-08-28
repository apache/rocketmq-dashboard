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
package org.apache.rocketmq.studio.audit;

/**
 * Operation audit vocabulary shared by Studio services.
 */
public final class OperationAuditConstants {

    private OperationAuditConstants() {
    }

    public static final class Operation {
        public static final String CREATE_TOPIC = "CREATE_TOPIC";
        public static final String UPDATE_TOPIC = "UPDATE_TOPIC";
        public static final String DELETE_TOPIC = "DELETE_TOPIC";

        public static final String CREATE_GROUP = "CREATE_GROUP";
        public static final String UPDATE_GROUP = "UPDATE_GROUP";
        public static final String DELETE_GROUP = "DELETE_GROUP";
        public static final String RESET_OFFSET = "RESET_OFFSET";

        public static final String ADD_PROXY_ADDRESS = "ADD_PROXY_ADDRESS";
        public static final String REMOVE_PROXY_ADDRESS = "REMOVE_PROXY_ADDRESS";
        public static final String RELOAD_PROXY_CONFIG = "RELOAD_PROXY_CONFIG";

        private Operation() {
        }
    }

    public static final class ResourceType {
        public static final String TOPIC = "TOPIC";
        public static final String GROUP = "GROUP";
        public static final String PROXY = "PROXY";

        private ResourceType() {
        }
    }

    public static final class Result {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        private Result() {
        }
    }
}
