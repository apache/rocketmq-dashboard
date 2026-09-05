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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.body.TopicList;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.dashboard.model.BrokerFailoverImpactReport;
import org.apache.rocketmq.dashboard.service.BrokerFailoverSimulationService;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class BrokerFailoverSimulationServiceImpl implements BrokerFailoverSimulationService {

    private final Logger log = LoggerFactory.getLogger(BrokerFailoverSimulationServiceImpl.class);

    @Autowired
    private MQAdminExt mqAdminExt;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private TopicService topicService;

    @Override
    public BrokerFailoverImpactReport simulateBrokerFailover(String brokerName) {
        BrokerFailoverImpactReport report = new BrokerFailoverImpactReport();
        report.setTargetBrokerName(brokerName);
        report.setSimulationTime(System.currentTimeMillis());

        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        } catch (Exception e) {
            log.warn("Failed to retrieve cluster info for simulation: {}", e.getMessage());
        }

        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            report.setTotalClusterBrokers(clusterInfo.getBrokerAddrTable().size());
            BrokerData targetData = clusterInfo.getBrokerAddrTable().get(brokerName);
            if (targetData != null) {
                report.setClusterName(targetData.getCluster());
            }
        }

        TopicList topicList = null;
        try {
            topicList = topicService.fetchAllTopicList();
        } catch (Exception e) {
            log.warn("Failed to fetch all topic list: {}", e.getMessage());
        }

        if (topicList == null || CollectionUtils.isEmpty(topicList.getTopicList())) {
            report.setHazardLevel("LOW");
            report.setAvailabilityScore(100.0);
            report.setActionPlan("No topics exist in cluster; broker failover has zero business impact.");
            return report;
        }

        Set<String> allTopics = topicList.getTopicList();
        report.setTotalClusterTopics(allTopics.size());

        List<BrokerFailoverImpactReport.ImpactedTopicDetail> impactedDetails = new ArrayList<>();
        int totalLossCount = 0;
        int degradedCount = 0;

        for (String topic : allTopics) {
            if (topic.startsWith("%RETRY%") || topic.startsWith("%DLQ%")) {
                continue;
            }

            TopicRouteData routeData;
            try {
                routeData = topicService.examineTopicRouteData(topic);
            } catch (Exception e) {
                continue;
            }

            if (routeData == null || CollectionUtils.isEmpty(routeData.getQueueDatas())) {
                continue;
            }

            int totalWriteQueues = 0;
            int lostWriteQueues = 0;
            boolean hostedOnTarget = false;

            for (QueueData qd : routeData.getQueueDatas()) {
                totalWriteQueues += qd.getWriteQueueNums();
                if (brokerName.equals(qd.getBrokerName())) {
                    hostedOnTarget = true;
                    lostWriteQueues += qd.getWriteQueueNums();
                }
            }

            if (hostedOnTarget && totalWriteQueues > 0) {
                BrokerFailoverImpactReport.ImpactedTopicDetail detail =
                        new BrokerFailoverImpactReport.ImpactedTopicDetail();
                detail.setTopic(topic);
                detail.setOriginalQueueCount(totalWriteQueues);
                detail.setLostQueueCount(lostWriteQueues);
                detail.setRemainingQueueCount(Math.max(0, totalWriteQueues - lostWriteQueues));

                double lossRatio = ((double) lostWriteQueues / totalWriteQueues) * 100.0;
                detail.setCapacityLossRatio(Math.round(lossRatio * 10.0) / 10.0);

                if (detail.getRemainingQueueCount() == 0) {
                    detail.setCompleteLoss(true);
                    detail.setRiskExplanation("CRITICAL: Topic is exclusively hosted on " + brokerName
                            + ". Broker failure will cause 100% write outage!");
                    totalLossCount++;
                } else {
                    detail.setCompleteLoss(false);
                    detail.setRiskExplanation("DEGRADED: Topic capacity reduced by " + detail.getCapacityLossRatio()
                            + "%. Traffic will failover to remaining " + detail.getRemainingQueueCount() + " queues.");
                    degradedCount++;
                }

                impactedDetails.add(detail);
            }
        }

        report.setImpactedTopics(impactedDetails);
        report.setImpactedTopicCount(impactedDetails.size());
        report.setTotalLossTopicCount(totalLossCount);
        report.setDegradedTopicCount(degradedCount);

        double score = 100.0 - (totalLossCount * 25.0 + degradedCount * 5.0);
        score = Math.max(0.0, Math.min(100.0, score));
        report.setAvailabilityScore(Math.round(score * 10.0) / 10.0);

        if (totalLossCount > 0) {
            report.setHazardLevel("CRITICAL");
            report.setActionPlan("HALT MAINTENANCE: " + totalLossCount
                    + " single-point topics will completely fail. Rebalance queues to other brokers first!");
        } else if (degradedCount > 0) {
            report.setHazardLevel("MEDIUM");
            report.setActionPlan("PROCEED WITH CAUTION: " + degradedCount
                    + " topics will suffer throughput reduction during maintenance window.");
        } else {
            report.setHazardLevel("LOW");
            report.setActionPlan("SAFE: Broker hosts no exclusive or shared active topic routes.");
        }

        return report;
    }
}
