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
package org.apache.rocketmq.dashboard.cli.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** YAML-serializable configuration model for rmqctl. Holds clusters, users, contexts, and the current-context pointer. Uses kubectl-style context management. */
@Data
public class CliConfig {

    private Map<String, ClusterEntry> clusters = new LinkedHashMap<>();
    private Map<String, UserEntry> users = new LinkedHashMap<>();
    private List<ContextEntry> contexts = new ArrayList<>();
    private String currentContext;

    @Data
    public static class ClusterEntry {
        private String name;
        private String namesrvAddr;
        private String proxyAddr;
        private String clusterType;
    }

    @Data
    public static class UserEntry {
        private String name;
        private String accessKey;
        private String secretKey;
    }

    @Data
    public static class ContextEntry {
        private String name;
        private String cluster;
        private String user;
        private String namespace;
    }
}
