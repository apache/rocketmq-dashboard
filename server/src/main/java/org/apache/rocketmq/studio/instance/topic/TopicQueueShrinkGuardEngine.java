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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.instance.topic.TopicShrinkPrecheckVO.GracefulDrainStepVO;
import org.apache.rocketmq.studio.instance.topic.TopicShrinkPrecheckVO.TruncatedQueueRiskVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class TopicQueueShrinkGuardEngine {

    /**
     * Evaluates the risk of shrinking topic write/read queue count from currentQueueNum to targetQueueNum.
     * Queues with queueId >= targetQueueNum will be truncated from write route. If any such queue has
     * active unconsumed messages or pending lags, shrinking immediately causes silent message retention.
     */
    public TopicShrinkPrecheckVO precheckQueueShrink(String topic, int currentQueueNum, int targetQueueNum,
                                                   List<TopicQueueStatsVO> queueStatsList) {
        TopicShrinkPrecheckVO report = TopicShrinkPrecheckVO.builder()
                .topic(topic)
                .currentQueueNum(currentQueueNum)
                .targetQueueNum(targetQueueNum)
                .safeToShrink(true)
                .precheckTimestamp(LocalDateTime.now())
                .truncatedQueues(new ArrayList<>())
                .warnings(new ArrayList<>())
                .recommendedSteps(new ArrayList<>())
                .build();

        if (targetQueueNum >= currentQueueNum) {
            report.setSafeToShrink(true);
            report.getWarnings().add("Target queue count is greater than or equal to current count; this is an expansion or no-op.");
            report.getRecommendedSteps().add(GracefulDrainStepVO.builder()
                    .stepOrder(1)
                    .action("DIRECT_APPLY")
                    .description("Target queue count expands or preserves existing capacity. Safe to apply immediately without backlog drainage.")
                    .estimatedTimeSeconds(0L)
                    .build());
            return report;
        }

        if (targetQueueNum <= 0) {
            report.setSafeToShrink(false);
            report.getWarnings().add("Target queue count must be positive.");
            return report;
        }

        long totalPendingOnTruncated = 0L;
        List<TruncatedQueueRiskVO> riskList = new ArrayList<>();

        if (queueStatsList != null) {
            for (TopicQueueStatsVO stat : queueStatsList) {
                int qId = stat.getQueueId();
                if (qId >= targetQueueNum) {
                    long minOffset = stat.getMinOffset() == null ? 0L : stat.getMinOffset();
                    long maxOffset = stat.getMaxOffset() == null ? 0L : stat.getMaxOffset();
                    long backlog = Math.max(0, maxOffset - minOffset);

                    if (backlog > 0) {
                        totalPendingOnTruncated += backlog;
                        String risk = backlog > 10_000L ? "CRITICAL" : (backlog > 100L ? "HIGH" : "LOW");
                        riskList.add(TruncatedQueueRiskVO.builder()
                                .brokerName(stat.getBrokerName())
                                .queueId(qId)
                                .minOffset(minOffset)
                                .maxOffset(maxOffset)
                                .unconsumedLag(backlog)
                                .riskLevel(risk)
                                .consumerGroupWithMaxLag("POTENTIAL_SUBSCRIBERS")
                                .build());
                    }
                }
            }
        }

        report.setTruncatedQueues(riskList);
        report.setTotalUnconsumedMessagesOnTruncatedQueues(totalPendingOnTruncated);

        if (totalPendingOnTruncated > 0) {
            report.setSafeToShrink(false);
            report.getWarnings().add(String.format(
                    "DANGER: Truncating queues [%d to %d] will strand %d unconsumed messages. Wait for consumers to drain before shrinking.",
                    targetQueueNum, currentQueueNum - 1, totalPendingOnTruncated));

            // Generate graceful migration steps
            report.getRecommendedSteps().add(GracefulDrainStepVO.builder()
                    .stepOrder(1)
                    .action("SET_WRITE_QUEUE_NUM_FIRST")
                    .description(String.format("Step 1: Set writeQueueNums=%d while maintaining readQueueNums=%d to stop publishing to truncated queues.",
                            targetQueueNum, currentQueueNum))
                    .estimatedTimeSeconds(10L)
                    .build());

            long estimatedDrainSeconds = Math.max(30L, Math.min(3600L, totalPendingOnTruncated / 50L));
            report.getRecommendedSteps().add(GracefulDrainStepVO.builder()
                    .stepOrder(2)
                    .action("WAIT_CONSUMER_CATCHUP")
                    .description(String.format("Step 2: Wait until consumers drain all %d messages from queues [%d-%d].",
                            totalPendingOnTruncated, targetQueueNum, currentQueueNum - 1))
                    .estimatedTimeSeconds(estimatedDrainSeconds)
                    .build());

            report.getRecommendedSteps().add(GracefulDrainStepVO.builder()
                    .stepOrder(3)
                    .action("FINALIZE_READ_QUEUE_SHRINK")
                    .description(String.format("Step 3: Once lag drops to zero, update readQueueNums=%d to finalize queue shrinkage.",
                            targetQueueNum))
                    .estimatedTimeSeconds(5L)
                    .build());
        } else {
            report.setSafeToShrink(true);
            report.getWarnings().add("Safe to shrink: all queues to be truncated have 0 unconsumed backlogs.");
            report.getRecommendedSteps().add(GracefulDrainStepVO.builder()
                    .stepOrder(1)
                    .action("DIRECT_SHRINK")
                    .description("Truncated queues have zero unconsumed messages. Safe to shrink write/read queues directly.")
                    .estimatedTimeSeconds(5L)
                    .build());
        }

        return report;
    }
}
