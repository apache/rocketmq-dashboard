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
package org.apache.rocketmq.dashboard.task;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.stats.Stats;
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.remoting.protocol.body.BrokerStatsData;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.command.stats.StatsAllSubCommand;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
public class StorageLatencyCollectTask implements Runnable {

    private String topic;
    private MQAdminExt mqAdminExt;
    private DashboardCollectService dashboardCollectService;

    public StorageLatencyCollectTask(String topic, MQAdminExt mqAdminExt,
                                      DashboardCollectService dashboardCollectService) {
        this.topic = topic;
        this.mqAdminExt = mqAdminExt;
        this.dashboardCollectService = dashboardCollectService;
    }

    @Override
    public void run() {
        Date date = new Date();
        try {
            TopicRouteData topicRouteData = mqAdminExt.examineTopicRouteInfo(topic);
            GroupList groupList = mqAdminExt.queryTopicConsumeByWho(topic);

            double putLatencyAvg = 0;
            double getLatencyAvg = 0;
            long fallSizeTotal = 0;
            double fallTimeAvg = 0;
            int brokerCount = 0;

            // Collect TOPIC_PUT_LATENCY (CommitLog write latency) from each broker
            for (BrokerData bd : topicRouteData.getBrokerDatas()) {
                String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                if (masterAddr != null) {
                    try {
                        BrokerStatsData putLatencyData = mqAdminExt.viewBrokerStatsData(
                                masterAddr, Stats.TOPIC_PUT_LATENCY, topic);
                        putLatencyAvg += putLatencyData.getStatsMinute().getTps();
                        brokerCount++;
                    } catch (Exception e) {
                        log.debug("TOPIC_PUT_LATENCY not available for topic [{}]: {}", topic, e.getMessage());
                    }
                }
            }

            // Collect GROUP_GET_LATENCY, GROUP_GET_FALL_SIZE, GROUP_GET_FALL_TIME from each broker for each consumer group
            if (groupList != null && !groupList.getGroupList().isEmpty()) {
                int groupCount = 0;
                for (String group : groupList.getGroupList()) {
                    for (BrokerData bd : topicRouteData.getBrokerDatas()) {
                        String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                        if (masterAddr != null) {
                            String statsKey = String.format("%s@%s", topic, group);
                            try {
                                BrokerStatsData getLatencyData = mqAdminExt.viewBrokerStatsData(
                                        masterAddr, Stats.GROUP_GET_LATENCY, statsKey);
                                getLatencyAvg += getLatencyData.getStatsMinute().getTps();
                            } catch (Exception e) {
                                log.debug("GROUP_GET_LATENCY not available for topic [{}], group [{}]: {}",
                                        topic, group, e.getMessage());
                            }
                            try {
                                BrokerStatsData fallSizeData = mqAdminExt.viewBrokerStatsData(
                                        masterAddr, Stats.GROUP_GET_FALL_SIZE, statsKey);
                                fallSizeTotal += StatsAllSubCommand.compute24HourSum(fallSizeData);
                            } catch (Exception e) {
                                log.debug("GROUP_GET_FALL_SIZE not available for topic [{}], group [{}]: {}",
                                        topic, group, e.getMessage());
                            }
                            try {
                                BrokerStatsData fallTimeData = mqAdminExt.viewBrokerStatsData(
                                        masterAddr, Stats.GROUP_GET_FALL_TIME, statsKey);
                                fallTimeAvg += fallTimeData.getStatsMinute().getTps();
                                groupCount++;
                            } catch (Exception e) {
                                log.debug("GROUP_GET_FALL_TIME not available for topic [{}], group [{}]: {}",
                                        topic, group, e.getMessage());
                            }
                        }
                    }
                }
                if (groupCount > 0) {
                    getLatencyAvg /= groupCount;
                    fallTimeAvg /= groupCount;
                }
            }

            if (brokerCount > 0) {
                putLatencyAvg /= brokerCount;
            }

            List<String> list;
            try {
                list = dashboardCollectService.getStorageLatencyMap().get(topic);
            } catch (ExecutionException e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
            if (null == list) {
                list = Lists.newArrayList();
            }

            list.add(date.getTime() + ","
                    + new BigDecimal(putLatencyAvg).setScale(5, BigDecimal.ROUND_HALF_UP) + ","
                    + new BigDecimal(getLatencyAvg).setScale(5, BigDecimal.ROUND_HALF_UP) + ","
                    + fallSizeTotal + ","
                    + new BigDecimal(fallTimeAvg).setScale(5, BigDecimal.ROUND_HALF_UP));
            dashboardCollectService.getStorageLatencyMap().put(topic, list);
        } catch (Exception e) {
            log.error("Failed to collect storage latency data for topic: {}", topic, e);
        }
    }
}