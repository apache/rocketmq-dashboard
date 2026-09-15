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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupProgressOutput(
        String cluster,
        String group,
        String topic,
        long totalLag,
        List<QueueProgress> queues) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueueProgress(
            String broker,
            int queueId,
            long brokerOffset,
            long consumerOffset,
            long lag) {

        public static QueueProgress from(QueueProgressVO queue) {
            if (queue == null) {
                return null;
            }
            return new QueueProgress(
                    queue.getBroker(),
                    queue.getQueueId(),
                    queue.getBrokerOffset(),
                    queue.getConsumerOffset(),
                    queue.getDiffTotal());
        }
    }

    public static GroupProgressOutput from(String cluster, String group, String topic, List<QueueProgressVO> progress) {
        List<QueueProgressVO> filtered = progress;
        if (topic != null && !topic.isBlank()) {
            filtered = (progress == null ? List.<QueueProgressVO>of() : progress).stream()
                    .filter(q -> topic.equals(q.getTopic()))
                    .toList();
        }
        List<QueueProgress> queues = (filtered == null ? List.<QueueProgressVO>of() : filtered).stream()
                .map(QueueProgress::from)
                .toList();
        long totalLag = queues.stream().anyMatch(queue -> queue.lag() == -1L)
                ? -1L
                : queues.stream().mapToLong(QueueProgress::lag).sum();
        return new GroupProgressOutput(
                cluster,
                group,
                topic,
                totalLag,
                queues);
    }
}
