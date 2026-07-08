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
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.remoting.protocol.body.BrokerStatsData;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.command.stats.StatsAllSubCommand;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Slf4j
public class HotTopicCollectTask implements Runnable {

    private MQAdminExt mqAdminExt;
    private DashboardCollectService dashboardCollectService;

    public HotTopicCollectTask(MQAdminExt mqAdminExt,
                                DashboardCollectService dashboardCollectService) {
        this.mqAdminExt = mqAdminExt;
        this.dashboardCollectService = dashboardCollectService;
    }

    @Override
    public void run() {
        Date date = new Date();
        try {
            TopicList topicList = mqAdminExt.fetchAllTopicList();
            Set<String> topicSet = topicList.getTopicList();
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            Set<Map.Entry<String, BrokerData>> clusterEntries = clusterInfo.getBrokerAddrTable().entrySet();

            for (String topic : topicSet) {
                if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)
                        || topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)
                        || TopicValidator.isSystemTopic(topic)) {
                    continue;
                }

                double totalPutTps = 0;
                long totalPutNumsToday = 0;
                double totalPutSizeTps = 0;
                long totalPutSizeToday = 0;

                for (Map.Entry<String, BrokerData> clusterEntry : clusterEntries) {
                    String brokerName = clusterEntry.getKey();
                    HashMap<Long, String> addrs = clusterEntry.getValue().getBrokerAddrs();
                    String masterAddr = addrs.get(MixAll.MASTER_ID);
                    if (masterAddr == null) {
                        continue;
                    }

                    try {
                        BrokerStatsData putData = mqAdminExt.viewBrokerStatsData(
                                masterAddr, Stats.TOPIC_PUT_NUMS, topic);
                        totalPutTps += putData.getStatsMinute().getTps();
                        totalPutNumsToday += StatsAllSubCommand.compute24HourSum(putData);
                    } catch (Exception e) {
                        log.debug("TOPIC_PUT_NUMS not available for topic [{}] on broker [{}]: {}",
                                topic, brokerName, e.getMessage());
                    }

                    try {
                        BrokerStatsData sizeData = mqAdminExt.viewBrokerStatsData(
                                masterAddr, Stats.TOPIC_PUT_SIZE, topic);
                        totalPutSizeTps += sizeData.getStatsMinute().getTps();
                        totalPutSizeToday += StatsAllSubCommand.compute24HourSum(sizeData);
                    } catch (Exception e) {
                        log.debug("TOPIC_PUT_SIZE not available for topic [{}] on broker [{}]: {}",
                                topic, brokerName, e.getMessage());
                    }
                }

                if (totalPutTps > 0 || totalPutNumsToday > 0
                        || totalPutSizeTps > 0 || totalPutSizeToday > 0) {
                    String key = topic;
                    List<String> list;
                    try {
                        list = dashboardCollectService.getHotTopicMap().get(key);
                    } catch (ExecutionException e) {
                        Throwables.throwIfUnchecked(e);
                        throw new RuntimeException(e);
                    }
                    if (null == list) {
                        list = Lists.newArrayList();
                    }

                    list.add(date.getTime() + ","
                            + totalPutTps + ","
                            + totalPutNumsToday + ","
                            + totalPutSizeTps + ","
                            + totalPutSizeToday);
                    dashboardCollectService.getHotTopicMap().put(key, list);
                }
            }
        } catch (Exception e) {
            log.error("Failed to collect hot topic data", e);
        }
    }
}