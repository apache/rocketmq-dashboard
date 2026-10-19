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
package org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.topic.LiteTopicItemVO;

import java.util.List;

/**
 * One pattern-level LiteTopic aggregate. Statistics the broker cannot report stay absent
 * instead of being zero-filled: an unknown backlog is not a zero backlog.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiteTopicListItem(
        String topicPattern,
        String namespace,
        Integer topicCount,
        Integer consumerCount,
        Long totalBacklog,
        Long averageTTL,
        String ttlStatus,
        Long lastActiveTime,
        List<String> sessionIds) {

    public static LiteTopicListItem from(LiteTopicItemVO vo) {
        return new LiteTopicListItem(
                vo.getTopicPattern(),
                vo.getNamespace(),
                vo.getTopicCount(),
                vo.getConsumerCount(),
                vo.getTotalBacklog(),
                vo.getAverageTTL(),
                vo.getTtlStatus(),
                vo.getLastActiveTime(),
                vo.getSessionIds() == null ? null : List.copyOf(vo.getSessionIds()));
    }
}
