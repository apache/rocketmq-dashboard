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
package org.apache.rocketmq.studio.cluster.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProducerHealthAuditReportVO {

    private String topic;
    private String producerGroup;
    private int totalProducerClients;
    private boolean multiIpRedundancy;
    private String healthSummary; // HEALTHY, WARNING_SINGLE_CLIENT, WARNING_LEGACY_SDK
    @Builder.Default
    private Map<String, Integer> languageDistribution = Map.of();
    @Builder.Default
    private Map<String, Integer> versionDistribution = Map.of();
    @Builder.Default
    private List<ProducerClientRiskVO> clientRisks = new ArrayList<>();
    @Builder.Default
    private List<String> governanceRecommendations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProducerClientRiskVO {
        private String clientId;
        private String clientAddr;
        private String language;
        private String versionDesc;
        private String riskType; // OUTDATED_SDK_VERSION, LOOPBACK_BINDING, UNRECOGNIZED_LANGUAGE
        private String detail;
    }
}
