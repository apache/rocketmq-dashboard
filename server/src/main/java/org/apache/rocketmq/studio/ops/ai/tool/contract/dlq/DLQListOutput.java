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
package org.apache.rocketmq.studio.ops.ai.tool.contract.dlq;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DLQListOutput(
        String cluster,
        String group,
        Integer page,
        Integer pageSize,
        Long total,
        java.util.List<?> items) {

    public static DLQListOutput ofGroups(
            String cluster,
            Integer page,
            Integer pageSize,
            Long total,
            java.util.List<DLQGroupItem> items) {
        return new DLQListOutput(cluster, null, page, pageSize, total, items);
    }

    public static DLQListOutput ofMessages(
            String cluster,
            String group,
            Integer page,
            Integer pageSize,
            Long total,
            java.util.List<DLQMessageItem> items) {
        return new DLQListOutput(cluster, group, page, pageSize, total, items);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DLQGroupItem(
            String groupName,
            String dlqTopic,
            long messageCount,
            int retryCount,
            String status,
            boolean statsAvailable,
            String lastEnqueueTime) {

        public static DLQGroupItem from(
                org.apache.rocketmq.studio.instance.dlq.DLQGroupVO group) {
            return new DLQGroupItem(
                    group.getGroupName(),
                    group.getDlqTopic(),
                    group.getMessageCount(),
                    group.getRetryCount(),
                    group.getStatus(),
                    group.isStatsAvailable(),
                    group.getLastEnqueueTime() != null
                            ? group.getLastEnqueueTime().toString() : null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DLQMessageItem(
            String msgId,
            String topic,
            int queueId,
            long offset,
            long storeTime,
            String keys,
            String body) {

        public static DLQMessageItem from(
                org.apache.rocketmq.studio.instance.dlq.DLQMessageVO message) {
            return new DLQMessageItem(
                    message.getMsgId(),
                    message.getTopic(),
                    message.getQueueId(),
                    message.getOffset(),
                    message.getStoreTime(),
                    message.getKeys(),
                    message.getBody());
        }
    }
}
