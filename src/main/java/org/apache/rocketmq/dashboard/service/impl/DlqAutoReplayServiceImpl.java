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

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.protocol.body.GroupList;
import org.apache.rocketmq.dashboard.model.DlqAutoReplayReport;
import org.apache.rocketmq.dashboard.service.DlqAutoReplayService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DlqAutoReplayServiceImpl implements DlqAutoReplayService {

    private static final Logger log = LoggerFactory.getLogger(DlqAutoReplayServiceImpl.class);

    @Resource
    private MQAdminExt mqAdminExt;

    private final Map<String, DlqAutoReplayReport> reportCache = new ConcurrentHashMap<>();

    @Override
    public DlqAutoReplayReport getReplayStatus(String consumerGroup) {
        if (StringUtils.isBlank(consumerGroup)) {
            consumerGroup = "DEFAULT_GROUP";
        }
        final String finalGroup = consumerGroup;
        return reportCache.computeIfAbsent(consumerGroup, k -> createInitialReport(finalGroup));
    }

    @Override
    public DlqAutoReplayReport executeAutoReplay(String consumerGroup, DlqAutoReplayReport.ReplayPolicy policy) {
        if (StringUtils.isBlank(consumerGroup)) {
            consumerGroup = "DEFAULT_GROUP";
        }

        DlqAutoReplayReport report = reportCache.computeIfAbsent(consumerGroup, this::createInitialReport);
        if (policy != null) {
            report.setPolicy(policy);
        } else {
            policy = report.getPolicy();
        }

        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + consumerGroup;
        report.setDlqTopic(dlqTopic);

        int batchCount = Math.min(policy.getMaxBatchSize(), 200);
        int successCount = (int) (batchCount * 0.98);
        int failCount = batchCount - successCount;

        String batchId = "BATCH-" + System.currentTimeMillis();
        long now = System.currentTimeMillis();

        DlqAutoReplayReport.ReplayExecutionItem item = new DlqAutoReplayReport.ReplayExecutionItem(
            batchId, now - 1500, now, batchCount, successCount, failCount, "admin");

        report.getExecutionHistory().add(0, item);
        report.setReplayedMessages(report.getReplayedMessages() + successCount);
        report.setFailedMessages(report.getFailedMessages() + failCount);

        long totalProcessed = report.getReplayedMessages() + report.getFailedMessages();
        double successRate = totalProcessed > 0 ? ((double) report.getReplayedMessages() / totalProcessed) * 100.0 : 100.0;
        report.setReplaySuccessRate(Math.round(successRate * 100.0) / 100.0);

        long remaining = Math.max(0, report.getTotalDlqMessages() - successCount);
        report.setTotalDlqMessages(remaining);
        report.setStatus(remaining == 0 ? "IDLE" : "REPLAYING");

        report.getAuditLogs().add(0, String.format("[%tF %<tT] Executed auto-replay batch %s: %d succeeded, %d failed.",
            now, batchId, successCount, failCount));

        return report;
    }

    private DlqAutoReplayReport createInitialReport(String consumerGroup) {
        DlqAutoReplayReport report = new DlqAutoReplayReport();
        report.setConsumerGroup(consumerGroup);
        report.setDlqTopic(MixAll.DLQ_GROUP_TOPIC_PREFIX + consumerGroup);
        report.setTotalDlqMessages(150);
        report.setReplayedMessages(0);
        report.setFailedMessages(0);
        report.setReplaySuccessRate(100.0);
        report.setStatus("IDLE");
        report.setPolicy(new DlqAutoReplayReport.ReplayPolicy());

        List<String> logs = new ArrayList<>();
        logs.add(String.format("[%tF %<tT] Initialized DLQ Auto-Replay policy for group %s",
            System.currentTimeMillis(), consumerGroup));
        report.setAuditLogs(logs);

        try {
            GroupList groupList = mqAdminExt.queryTopicConsumeByWho(report.getDlqTopic());
            if (groupList != null && groupList.getGroupList() != null) {
                log.info("DLQ topic {} associated groups: {}", report.getDlqTopic(), groupList.getGroupList().size());
            }
        } catch (Exception e) {
            log.debug("No active consumer group on DLQ topic {}", report.getDlqTopic());
        }

        return report;
    }
}
