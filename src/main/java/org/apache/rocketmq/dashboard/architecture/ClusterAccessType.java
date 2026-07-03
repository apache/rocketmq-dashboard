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
package org.apache.rocketmq.dashboard.architecture;

/**
 *
 *
 */
public enum ClusterAccessType {

    /**
 *
     */
    V4_NAMESRV("v4-namesrv", "4.0 Direct Connection"),

    /**
 *
     */
    V5_PROXY_LOCAL("v5-proxy-local", "5.0 Proxy Local Mode"),

    /**
 *
     */
    V5_PROXY_CLUSTER("v5-proxy-cluster", "5.0 Proxy Cluster Mode"),

    /**
 *
     */
    CLOUD_ALIYUN("cloud-aliyun", "Aliyun RocketMQ"),
    CLOUD_TENCENT("cloud-tencent", "Tencent Cloud RocketMQ"),
    CLOUD_HUAWEI("cloud-huawei", "Huawei Cloud RocketMQ");

    private final String value;
    private final String description;

    ClusterAccessType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
 *
     */
    public boolean isCloudProvider() {
        return name().startsWith("CLOUD_");
    }

    /**
 *
     */
    public boolean isV5Architecture() {
        return name().startsWith("V5_");
    }

    /**
 *
     */
    public boolean isV4Architecture() {
        return V4_NAMESRV.equals(this);
    }

    public static ClusterAccessType fromValue(String value) {
        for (ClusterAccessType type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown cluster access type: " + value);
    }
}