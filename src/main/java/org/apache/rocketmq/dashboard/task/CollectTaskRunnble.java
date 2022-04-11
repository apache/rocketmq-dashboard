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
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.protocol.body.BrokerStatsData;
import org.apache.rocketmq.common.protocol.body.GroupList;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.store.stats.BrokerStatsManager;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.command.stats.StatsAllSubCommand;

@Slf4j
public class CollectTaskRunnble implements Runnable {

    private String topic;

    private MQAdminExt mqAdminExt;

    private DashboardCollectService dashboardCollectService;

    public CollectTaskRunnble(String topic, MQAdminExt mqAdminExt,
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
            double inTPS = 0;
            long inMsgCntToday = 0;
            double outTPS = 0;
            long outMsgCntToday = 0;
            for (BrokerData bd : topicRouteData.getBrokerDatas()) {
                String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                if (masterAddr != null) {
                    try {
                        BrokerStatsData bsd = mqAdminExt.viewBrokerStatsData(masterAddr, BrokerStatsManager.TOPIC_PUT_NUMS, topic);
                        inTPS += bsd.getStatsMinute().getTps();
                        inMsgCntToday += StatsAllSubCommand.compute24HourSum(bsd);
                    } catch (Exception e) {
                        log.warn("Exception caught: mqAdminExt get broker stats data TOPIC_PUT_NUMS failed, topic [{}]", topic, e.getMessage());
                    }
                }
            }
            if (groupList != null && !groupList.getGroupList().isEmpty()) {
                for (String group : groupList.getGroupList()) {
                    for (BrokerData bd : topicRouteData.getBrokerDatas()) {
                        String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                        if (masterAddr != null) {
                            try {
                                String statsKey = String.format("%s@%s", topic, group);
                                BrokerStatsData bsd = mqAdminExt.viewBrokerStatsData(masterAddr, BrokerStatsManager.GROUP_GET_NUMS, statsKey);
                                outTPS += bsd.getStatsMinute().getTps();
                                outMsgCntToday += StatsAllSubCommand.compute24HourSum(bsd);
                            } catch (Exception e) {
                                log.warn("Exception caught: mqAdminExt get broker stats data GROUP_GET_NUMS failed, topic [{}], group [{}]", topic, group, e.getMessage());
                            }
                        }
                    }
                }
            }

            List<String> list;
            try {
                list = dashboardCollectService.getTopicMap().get(topic);
            } catch (ExecutionException e) {
                throw Throwables.propagate(e);
            }
            if (null == list) {
                list = Lists.newArrayList();
            }

            list.add(date.getTime() + "," + new BigDecimal(inTPS).setScale(5, BigDecimal.ROUND_HALF_UP) + "," + inMsgCntToday + "," + new BigDecimal(outTPS).setScale(5, BigDecimal.ROUND_HALF_UP) + "," + outMsgCntToday);
            dashboardCollectService.getTopicMap().put(topic, list);
        } catch (Exception e) {
            log.error("Failed to collect topic: {} data", topic, e);
        }
    }
}
