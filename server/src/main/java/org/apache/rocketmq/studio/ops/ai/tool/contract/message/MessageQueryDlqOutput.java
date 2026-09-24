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
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageQueryDlqOutput(
        String instanceId,
        String group,
        Integer page,
        Integer pageSize,
        Long total,
        List<?> items) {

    public static MessageQueryDlqOutput ofGroups(
            String instanceId,
            Integer page,
            Integer pageSize,
            Long total,
            List<DlqGroupItem> items) {
        return new MessageQueryDlqOutput(instanceId, null, page, pageSize, total, items);
    }

    public static MessageQueryDlqOutput ofMessages(
            String instanceId,
            String group,
            Integer page,
            Integer pageSize,
            Long total,
            List<DlqMessageItem> items) {
        return new MessageQueryDlqOutput(instanceId, group, page, pageSize, total, items);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DlqGroupItem(
            String groupName,
            String dlqTopic,
            long messageCount,
            int retryCount,
            String status,
            boolean statsAvailable,
            String lastEnqueueTime) {

        public static DlqGroupItem from(DLQGroupVO group) {
            return new DlqGroupItem(
                    group.getGroupName(),
                    group.getDlqTopic(),
                    group.getMessageCount(),
                    group.getRetryCount(),
                    group.getStatus(),
                    group.isStatsAvailable(),
                    group.getLastEnqueueTime());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DlqMessageItem(
            String msgId,
            String topic,
            int queueId,
            long offset,
            long storeTime,
            String keys,
            String body) {

        public static DlqMessageItem from(DLQMessageVO message) {
            return new DlqMessageItem(
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
