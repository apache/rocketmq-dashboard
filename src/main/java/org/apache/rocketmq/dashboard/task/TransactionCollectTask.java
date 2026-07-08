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
public class TransactionCollectTask implements Runnable {

    private String topic;
    private MQAdminExt mqAdminExt;
    private DashboardCollectService dashboardCollectService;

    public TransactionCollectTask(String topic, MQAdminExt mqAdminExt,
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

            double sndbckPutTps = 0;
            long sndbckPutNumsToday = 0;
            double groupCkTps = 0;
            long groupCkNumsToday = 0;

            // Collect SNDBCK_PUT_NUMS (half-message sendback rate) from each broker
            for (BrokerData bd : topicRouteData.getBrokerDatas()) {
                String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                if (masterAddr != null) {
                    try {
                        BrokerStatsData bsd = mqAdminExt.viewBrokerStatsData(masterAddr, Stats.SNDBCK_PUT_NUMS, topic);
                        sndbckPutTps += bsd.getStatsMinute().getTps();
                        sndbckPutNumsToday += StatsAllSubCommand.compute24HourSum(bsd);
                    } catch (Exception e) {
                        // SNDBCK_PUT_NUMS may not exist for non-transaction topics, this is expected
                        log.debug("SNDBCK_PUT_NUMS not available for topic [{}]: {}", topic, e.getMessage());
                    }
                }
            }

            // Collect GROUP_CK_NUMS (transaction check count) from each broker for each consumer group
            if (groupList != null && !groupList.getGroupList().isEmpty()) {
                for (String group : groupList.getGroupList()) {
                    for (BrokerData bd : topicRouteData.getBrokerDatas()) {
                        String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                        if (masterAddr != null) {
                            try {
                                String statsKey = String.format("%s@%s", topic, group);
                                BrokerStatsData bsd = mqAdminExt.viewBrokerStatsData(masterAddr, Stats.GROUP_CK_NUMS, statsKey);
                                groupCkTps += bsd.getStatsMinute().getTps();
                                groupCkNumsToday += StatsAllSubCommand.compute24HourSum(bsd);
                            } catch (Exception e) {
                                // GROUP_CK_NUMS may not exist for non-transaction groups, this is expected
                                log.debug("GROUP_CK_NUMS not available for topic [{}], group [{}]: {}",
                                        topic, group, e.getMessage());
                            }
                        }
                    }
                }
            }

            // Only store data if there is transaction activity for this topic
            if (sndbckPutTps > 0 || sndbckPutNumsToday > 0 || groupCkTps > 0 || groupCkNumsToday > 0) {
                List<String> list;
                try {
                    list = dashboardCollectService.getTransactionMap().get(topic);
                } catch (ExecutionException e) {
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
                if (null == list) {
                    list = Lists.newArrayList();
                }

                list.add(date.getTime() + ","
                        + new BigDecimal(sndbckPutTps).setScale(5, BigDecimal.ROUND_HALF_UP) + ","
                        + sndbckPutNumsToday + ","
                        + new BigDecimal(groupCkTps).setScale(5, BigDecimal.ROUND_HALF_UP) + ","
                        + groupCkNumsToday);
                dashboardCollectService.getTransactionMap().put(topic, list);
            }
        } catch (Exception e) {
            log.error("Failed to collect transaction data for topic: {}", topic, e);
        }
    }
}
