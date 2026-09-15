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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.TopicVO;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopicListItem(
        String name,
        String namespace,
        String clusterId,
        TopicType type,
        int writeQueues,
        int readQueues,
        TopicPerm perm,
        long messageCount,
        double tps,
        int consumerGroupCount) {

    public static TopicListItem from(TopicVO topic) {
        return new TopicListItem(
                topic.getName(),
                topic.getNamespace(),
                topic.getClusterId(),
                topic.getType(),
                topic.getWriteQueues(),
                topic.getReadQueues(),
                topic.getPerm(),
                topic.getMessageCount(),
                topic.getTps(),
                topic.getConsumerGroupCount());
    }
}
