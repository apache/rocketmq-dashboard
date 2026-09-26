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

import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.cluster.nameserver.NamesrvRouteConsistencyReportVO.NamesrvNodeRouteVO;
import org.apache.rocketmq.studio.cluster.nameserver.NamesrvRouteConsistencyReportVO.RouteDriftDetailVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Component
public class MultiNamesrvRouteConsistencyProbe {

    public NamesrvRouteConsistencyReportVO probe(String topic, Map<String, TopicRouteData> nodeRouteMap) {
        NamesrvRouteConsistencyReportVO report = NamesrvRouteConsistencyReportVO.builder()
                .topic(topic)
                .probeTimestamp(LocalDateTime.now())
                .totalNameServersQueried(nodeRouteMap == null ? 0 : nodeRouteMap.size())
                .fullyConsistent(true)
                .nodeRoutes(new ArrayList<>())
                .routeDrifts(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();

        if (nodeRouteMap == null || nodeRouteMap.isEmpty()) {
            report.setFullyConsistent(false);
            report.getWarnings().add("No NameServer endpoints were available to query route data for: " + topic);
            return report;
        }

        int successCount = 0;
        String baselineAddr = null;
        TopicRouteData baselineRoute = null;

        for (Map.Entry<String, TopicRouteData> entry : nodeRouteMap.entrySet()) {
            String addr = entry.getKey();
            TopicRouteData route = entry.getValue();

            if (route != null) {
                successCount++;
                if (baselineRoute == null) {
                    baselineRoute = route;
                    baselineAddr = addr;
                }

                int brokerCount = route.getBrokerDatas() != null ? route.getBrokerDatas().size() : 0;
                int queueDataCount = route.getQueueDatas() != null ? route.getQueueDatas().size() : 0;
                String sigHash = computeRouteSignature(route);

                report.getNodeRoutes().add(NamesrvNodeRouteVO.builder()
                        .namesrvAddr(addr)
                        .reachable(true)
                        .brokerCount(brokerCount)
                        .queueDataCount(queueDataCount)
                        .routeSignatureHash(sigHash)
                        .build());
            } else {
                report.getNodeRoutes().add(NamesrvNodeRouteVO.builder()
                        .namesrvAddr(addr)
                        .reachable(false)
                        .errorMessage("Unreachable or timeout during topic route query")
                        .build());
            }
        }

        report.setSuccessfulNameServers(successCount);

        if (successCount <= 1) {
            report.setFullyConsistent(true);
            report.getWarnings().add("Only single reachable NameServer found; multi-node route drift comparison skipped.");
            return report;
        }

        // Compare each node route against baseline
        Set<String> baselineBrokers = extractBrokerNames(baselineRoute);
        int baselineQueueCount = countTotalQueues(baselineRoute);

        for (Map.Entry<String, TopicRouteData> entry : nodeRouteMap.entrySet()) {
            String addr = entry.getKey();
            TopicRouteData route = entry.getValue();
            if (route == null || Objects.equals(addr, baselineAddr)) {
                continue;
            }

            Set<String> nodeBrokers = extractBrokerNames(route);
            if (!Objects.equals(baselineBrokers, nodeBrokers)) {
                report.setFullyConsistent(false);
                report.getRouteDrifts().add(RouteDriftDetailVO.builder()
                        .dimension("BROKER_DATA_MEMBERSHIP")
                        .description("Discrepancy in registered broker cluster members for topic")
                        .baselineValue(baselineBrokers.toString())
                        .deviantNamesrvAddr(addr)
                        .deviantValue(nodeBrokers.toString())
                        .build());
            }

            int nodeQueueCount = countTotalQueues(route);
            if (baselineQueueCount != nodeQueueCount) {
                report.setFullyConsistent(false);
                report.getRouteDrifts().add(RouteDriftDetailVO.builder()
                        .dimension("QUEUE_COUNT_MISMATCH")
                        .description("Discrepancy in allocated partition queue counts")
                        .baselineValue(String.valueOf(baselineQueueCount))
                        .deviantNamesrvAddr(addr)
                        .deviantValue(String.valueOf(nodeQueueCount))
                        .build());
            }
        }

        if (!report.isFullyConsistent()) {
            report.getWarnings().add(String.format(
                    "ROUTE DIVERGENCE DETECTED: %d inconsistency issue(s) found across NameServers for topic [%s]. " +
                            "Brokers may be experiencing registration delays or network splits.",
                    report.getRouteDrifts().size(), topic));
        } else {
            report.getWarnings().add("All NameServers serve 100% identical topic routing topologies.");
        }

        return report;
    }

    private String computeRouteSignature(TopicRouteData route) {
        if (route == null) {
            return "0";
        }
        int hash = 17;
        if (route.getBrokerDatas() != null) {
            for (BrokerData bd : route.getBrokerDatas()) {
                hash = 31 * hash + (bd.getBrokerName() != null ? bd.getBrokerName().hashCode() : 0);
            }
        }
        if (route.getQueueDatas() != null) {
            for (QueueData qd : route.getQueueDatas()) {
                hash = 31 * hash + qd.getWriteQueueNums();
                hash = 31 * hash + qd.getReadQueueNums();
                hash = 31 * hash + qd.getPerm();
            }
        }
        return Integer.toHexString(hash);
    }

    private Set<String> extractBrokerNames(TopicRouteData route) {
        Set<String> set = new TreeSet<>();
        if (route != null && route.getBrokerDatas() != null) {
            for (BrokerData bd : route.getBrokerDatas()) {
                if (bd.getBrokerName() != null) {
                    set.add(bd.getBrokerName());
                }
            }
        }
        return set;
    }

    private int countTotalQueues(TopicRouteData route) {
        int sum = 0;
        if (route != null && route.getQueueDatas() != null) {
            for (QueueData qd : route.getQueueDatas()) {
                sum += qd.getWriteQueueNums();
            }
        }
        return sum;
    }
}
