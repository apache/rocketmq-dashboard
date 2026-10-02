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

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.studio.cluster.client.ProducerHealthAuditReportVO.ProducerClientRiskVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProducerConnectionHealthAnalyzer {

    public ProducerHealthAuditReportVO analyze(String topic, String group, List<ProducerConnectionVO> connections) {
        ProducerHealthAuditReportVO report = ProducerHealthAuditReportVO.builder()
                .topic(topic)
                .producerGroup(group)
                .totalProducerClients(connections == null ? 0 : connections.size())
                .clientRisks(new ArrayList<>())
                .governanceRecommendations(new ArrayList<>())
                .build();

        if (connections == null || connections.isEmpty()) {
            report.setHealthSummary("WARNING_NO_CLIENTS");
            report.setMultiIpRedundancy(false);
            report.getGovernanceRecommendations().add(
                    String.format("No active producer connections found for topic [%s], group [%s]. " +
                            "Verify producer process health or long-polling channel establishment.", topic, group));
            report.getGovernanceRecommendations().add("Check client application deployment logs for RemotingConnectException or NameServer timeout.");
            return report;
        }

        Map<String, Integer> langDist = new HashMap<>();
        Map<String, Integer> verDist = new HashMap<>();
        Set<String> distinctIps = new HashSet<>();
        int legacyCount = 0;

        for (ProducerConnectionVO conn : connections) {
            String lang = StringUtils.isNotBlank(conn.getLanguage()) ? conn.getLanguage().trim().toUpperCase() : "UNKNOWN";
            String ver = StringUtils.isNotBlank(conn.getVersionDesc()) ? conn.getVersionDesc().trim() : "UNKNOWN";
            String addr = conn.getClientAddr() != null ? conn.getClientAddr().trim() : "";

            langDist.put(lang, langDist.getOrDefault(lang, 0) + 1);
            verDist.put(ver, verDist.getOrDefault(ver, 0) + 1);

            String ip = extractIp(addr);
            if (StringUtils.isNotBlank(ip)) {
                distinctIps.add(ip);
            }

            // Detect risks
            if ("127.0.0.1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
                report.getClientRisks().add(ProducerClientRiskVO.builder()
                        .clientId(conn.getClientId())
                        .clientAddr(conn.getClientAddr())
                        .language(lang)
                        .versionDesc(ver)
                        .riskType("LOOPBACK_BINDING")
                        .detail("Producer registered via loopback address. Multi-host communication will fail.")
                        .build());
            }

            if (ver.contains("V3_") || ver.contains("V4_0") || ver.contains("V4_1")) {
                legacyCount++;
                report.getClientRisks().add(ProducerClientRiskVO.builder()
                        .clientId(conn.getClientId())
                        .clientAddr(conn.getClientAddr())
                        .language(lang)
                        .versionDesc(ver)
                        .riskType("OUTDATED_SDK_VERSION")
                        .detail("Producer client uses legacy RocketMQ SDK version: " + ver)
                        .build());
            }

            if ("UNKNOWN".equals(lang)) {
                report.getClientRisks().add(ProducerClientRiskVO.builder()
                        .clientId(conn.getClientId())
                        .clientAddr(conn.getClientAddr())
                        .language(lang)
                        .versionDesc(ver)
                        .riskType("UNRECOGNIZED_LANGUAGE")
                        .detail("Producer client does not report valid client language identifier.")
                        .build());
            }
        }

        report.setLanguageDistribution(langDist);
        report.setVersionDistribution(verDist);
        boolean redundancy = distinctIps.size() > 1;
        report.setMultiIpRedundancy(redundancy);

        if (!redundancy && connections.size() > 0) {
            report.setHealthSummary("WARNING_SINGLE_HOST");
            report.getGovernanceRecommendations().add(
                    "All producer clients originate from a single host IP. Introduce multi-host redundancy to prevent single-point of failure.");
            report.getGovernanceRecommendations().add("Scale out producer containers across multiple physical nodes or Kubernetes availability zones.");
        } else if (legacyCount > 0) {
            report.setHealthSummary("WARNING_LEGACY_SDK");
            report.getGovernanceRecommendations().add(
                    String.format("Found %d client(s) on legacy SDK versions. Upgrade to RocketMQ 5.x SDK to support modern remoting features.", legacyCount));
            report.getGovernanceRecommendations().add("Older client protocol versions do not support modern gRPC/batching optimizations.");
        } else {
            report.setHealthSummary("HEALTHY");
            report.getGovernanceRecommendations().add("Producer client pool is distributed, active, and running compliant SDK versions.");
            report.getGovernanceRecommendations().add("All producer connections maintain active heartbeats with broker cluster.");
        }

        return report;
    }

    private String extractIp(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String clean = address.startsWith("/") ? address.substring(1) : address;
        int colon = clean.indexOf(':');
        return colon > 0 ? clean.substring(0, colon) : clean;
    }
}
