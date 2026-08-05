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
package org.apache.rocketmq.studio.cluster.nameserver;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NameServerConfigDiffVO {

    private String cluster;
    private boolean complete;
    private boolean driftDetected;
    private int nodeCount;
    private int reachableNodeCount;
    private List<String> comparedKeys;
    private List<NodeStatusVO> nodes;
    private List<ConfigDifferenceVO> differences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeStatusVO {
        private String address;
        private boolean reachable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigDifferenceVO {
        private String key;
        private List<ConfigValueVO> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigValueVO {
        private String address;
        private boolean configured;
        private String value;
    }
}
