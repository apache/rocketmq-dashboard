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
package com.rocketmq.studio.cluster.capability;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ClusterCapability {
    METADATA_READ("metadata-read"),
    METADATA_WRITE("metadata-write"),
    NAMESPACE("namespace"),
    TYPED_TOPIC("typed-topic"),
    LITE_TOPIC("lite-topic"),
    REMOTING_ADMIN("remoting-admin"),
    GRPC_ADMIN("grpc-admin"),
    POP_CONSUME("pop-consume"),
    BATCH_CONSUME("batch-consume"),
    ACL_1("acl-1"),
    ACL_2("acl-2"),
    CLIENT_RUNTIME_DETAIL("client-runtime-detail"),
    PROMETHEUS_METRICS("prometheus-metrics");

    private final String wireName;

    ClusterCapability(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String getWireName() {
        return wireName;
    }
}
