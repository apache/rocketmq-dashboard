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
import org.apache.rocketmq.common.stats.Stats;
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.remoting.protocol.body.BrokerStatsData;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.command.stats.StatsAllSubCommand;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Slf4j
public class NetworkThroughputCollectTask implements Runnable {

    private MQAdminExt mqAdminExt;
    private DashboardCollectService dashboardCollectService;

    public NetworkThroughputCollectTask(MQAdminExt mqAdminExt,
                                         DashboardCollectService dashboardCollectService) {
        this.mqAdminExt = mqAdminExt;
        this.dashboardCollectService = dashboardCollectService;
    }

    @Override
    public void run() {
        Date date = new Date();
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            Set<Map.Entry<String, BrokerData>> clusterEntries = clusterInfo.getBrokerAddrTable().entrySet();

            for (Map.Entry<String, BrokerData> clusterEntry : clusterEntries) {
                String brokerName = clusterEntry.getKey();
                HashMap<Long, String> addrs = clusterEntry.getValue().getBrokerAddrs();
                String masterAddr = addrs.get(org.apache.rocketmq.common.MixAll.MASTER_ID);
                if (masterAddr == null) {
                    continue;
                }

                double putTps = 0;
                long putNumsToday = 0;
                double getTps = 0;
                long getNumsToday = 0;

                try {
                    BrokerStatsData putData = mqAdminExt.viewBrokerStatsData(
                            masterAddr, Stats.BROKER_PUT_NUMS, brokerName);
                    putTps = putData.getStatsMinute().getTps();
                    putNumsToday = StatsAllSubCommand.compute24HourSum(putData);
                } catch (Exception e) {
                    log.debug("BROKER_PUT_NUMS not available for broker [{}]: {}", brokerName, e.getMessage());
                }

                try {
                    BrokerStatsData getData = mqAdminExt.viewBrokerStatsData(
                            masterAddr, Stats.BROKER_GET_NUMS, brokerName);
                    getTps = getData.getStatsMinute().getTps();
                    getNumsToday = StatsAllSubCommand.compute24HourSum(getData);
                } catch (Exception e) {
                    log.debug("BROKER_GET_NUMS not available for broker [{}]: {}", brokerName, e.getMessage());
                }

                String key = brokerName;
                List<String> list;
                try {
                    list = dashboardCollectService.getNetworkThroughputMap().get(key);
                } catch (ExecutionException e) {
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
                if (null == list) {
                    list = Lists.newArrayList();
                }

                list.add(date.getTime() + ","
                        + new BigDecimal(putTps).setScale(5, BigDecimal.ROUND_HALF_UP) + ","
                        + putNumsToday + ","
                        + new BigDecimal(getTps).setScale(5, BigDecimal.ROUND_HALF_UP) + ","
                        + getNumsToday);
                dashboardCollectService.getNetworkThroughputMap().put(key, list);
            }
        } catch (Exception e) {
            log.error("Failed to collect network throughput data", e);
        }
    }
}