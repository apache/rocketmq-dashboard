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
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.TopicGovernanceReport;
import org.apache.rocketmq.dashboard.model.TopicGovernanceReport.TopicGovernanceItem;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.TopicGovernanceService;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.apache.rocketmq.remoting.protocol.body.TopicConfigSerializeWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TopicGovernanceServiceImpl implements TopicGovernanceService {

    private static final Logger logger = LoggerFactory.getLogger(TopicGovernanceServiceImpl.class);

    @Autowired
    private TopicService topicService;

    @Autowired
    private ConsumerService consumerService;

    @Override
    public TopicGovernanceReport auditTopicLifecycle() {
        TopicGovernanceReport report = new TopicGovernanceReport();

        try {
            TopicConfigSerializeWrapper topicWrapper = topicService.fetchAllTopicList();
            if (topicWrapper == null || topicWrapper.getTopicConfigTable() == null) {
                return report;
            }

            Map<String, TopicConfig> topicConfigTable = topicWrapper.getTopicConfigTable();
            report.setTotalTopicsCount(topicConfigTable.size());

            int zombieCount = 0;
            int orphanCount = 0;
            int oversizedQueueCount = 0;

            for (Map.Entry<String, TopicConfig> entry : topicConfigTable.entrySet()) {
                String topic = entry.getKey();
                TopicConfig config = entry.getValue();

                if (StringUtils.isBlank(topic) || topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX) || topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX) || MixAll.isSysConsumerGroup(topic)) {
                    continue;
                }

                // Check Subscription Status
                List<GroupConsumeInfo> consumerGroups = null;
                try {
                    TopicList topicList = topicService.queryTopicConsumerByTopic(topic);
                    if (topicList != null && CollectionUtils.isNotEmpty(topicList.getTopicList())) {
                        consumerGroups = consumerService.queryGroupList(true, null);
                    }
                } catch (Exception ignored) {}

                int subGroupCount = consumerGroups != null ? consumerGroups.size() : 0;
                int readQueues = config.getReadQueueNums();
                int writeQueues = config.getWriteQueueNums();

                // 1. Orphan Topic Detection (No Consumer Subscribers)
                if (subGroupCount == 0) {
                    orphanCount++;
                    report.getGovernanceItems().add(new TopicGovernanceItem(
                            topic,
                            "ORPHAN_TOPIC",
                            readQueues,
                            writeQueues,
                            0,
                            0,
                            "Topic has no registered consumer groups subscribing to it",
                            "Confirm with producer owners and delete if obsolete"
                    ));
                }

                // 2. Oversized Queue Detection (Queue Count > 32 for low throughput topics)
                if (readQueues > 32 || writeQueues > 32) {
                    oversizedQueueCount++;
                    report.getGovernanceItems().add(new TopicGovernanceItem(
                            topic,
                            "OVERSIZED_QUEUES",
                            readQueues,
                            writeQueues,
                            subGroupCount,
                            0,
                            "Topic queue count is unusually high (" + readQueues + " queues), consuming extra memory overhead",
                            "Reduce queue count to match actual parallelism needs (e.g., 8 or 16 queues)"
                    ));
                }

                // 3. Zombie Topic Detection (Zero traffic & inactive)
                if (subGroupCount == 0 && readQueues <= 8) {
                    zombieCount++;
                    report.getGovernanceItems().add(new TopicGovernanceItem(
                            topic,
                            "ZOMBIE_TOPIC",
                            readQueues,
                            writeQueues,
                            0,
                            0,
                            "Topic has zero active consumers and low queue settings, likely an orphaned abandoned topic",
                            "Archive configuration and recycle topic resources"
                    ));
                }
            }

            report.setZombieTopicsCount(zombieCount);
            report.setOrphanTopicsCount(orphanCount);
            report.setOversizedQueueTopicsCount(oversizedQueueCount);

        } catch (Exception e) {
            logger.error("Failed to audit topic lifecycle governance", e);
        }

        return report;
    }
}
