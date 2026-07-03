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

import lombok.Data;
import java.util.Date;
import java.util.Map;

/**
 *
 *
 */
@Data
public class NamespaceInfo {

    /**
 *
     */
    private String namespaceName;

    /**
 *
     */
    private String displayName;

    /**
 *
     */
    private String description;

    /**
 *
     */
    private String clusterName;

    /**
 *
     */
    private Date createTime;

    /**
 *
     */
    private Date updateTime;

    /**
 *
     */
    private String status;

    /**
 *
     */
    private QuotaConfig quotaConfig;

    /**
 *
     */
    private Map<String, String> attributes;

    /**
 *
     */
    private boolean defaultNamespace;

    @Data
    public static class QuotaConfig {
        /**
 *
         */
        private Integer maxTopicCount;

        /**
 *
         */
        private Integer maxConsumerGroupCount;

        /**
 *
         */
        private Long storageQuotaGB;

        /**
 *
         */
        private Integer qpsLimit;

        /**
 *
         */
        private Integer connectionLimit;

        /**
 *
         */
        private LiteTopicQuota liteTopicQuota;
    }

    /**
 *
     */
    public boolean isValid() {
        return namespaceName != null && !namespaceName.trim().isEmpty();
    }

    /**
 *
     */
    public boolean isEnabled() {
        return "ENABLED".equalsIgnoreCase(status);
    }
}