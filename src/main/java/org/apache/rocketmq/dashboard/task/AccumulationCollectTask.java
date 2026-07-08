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
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
public class AccumulationCollectTask implements Runnable {

    private String topic;
    private MQAdminExt mqAdminExt;
    private DashboardCollectService dashboardCollectService;

    public AccumulationCollectTask(String topic, MQAdminExt mqAdminExt,
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
            long diffTotal = 0;

            if (groupList != null && !groupList.getGroupList().isEmpty()) {
                for (String group : groupList.getGroupList()) {
                    try {
                        ConsumeStats consumeStats = mqAdminExt.examineConsumeStats(group, topic);
                        if (consumeStats != null) {
                            diffTotal += consumeStats.computeTotalDiff();
                        }
                    } catch (Exception e) {
                        log.warn("Exception caught: examineConsumeStats failed for topic [{}], group [{}]",
                                topic, group, e.getMessage());
                    }
                }
            }

            List<String> list;
            try {
                list = dashboardCollectService.getAccumulationMap().get(topic);
            } catch (ExecutionException e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
            if (null == list) {
                list = Lists.newArrayList();
            }

            list.add(date.getTime() + "," + diffTotal);
            dashboardCollectService.getAccumulationMap().put(topic, list);
        } catch (Exception e) {
            log.error("Failed to collect accumulation data for topic: {}", topic, e);
        }
    }
}
