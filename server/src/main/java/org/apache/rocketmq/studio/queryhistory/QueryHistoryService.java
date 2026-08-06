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
package org.apache.rocketmq.studio.queryhistory;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.persistence.entity.RmqMessageQuery;
import org.apache.rocketmq.studio.persistence.entity.RmqTraceQuery;
import org.apache.rocketmq.studio.persistence.mapper.RmqMessageQueryMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTraceQueryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class QueryHistoryService {

    private final RmqMessageQueryMapper messageQueryMapper;
    private final RmqTraceQueryMapper traceQueryMapper;

    public QueryHistoryService(RmqMessageQueryMapper messageQueryMapper,
                               RmqTraceQueryMapper traceQueryMapper) {
        this.messageQueryMapper = messageQueryMapper;
        this.traceQueryMapper = traceQueryMapper;
    }

    public void recordMessageQuery(String queryType, String topic, String msgId,
                                   String tag, String key, Long startTime,
                                   Long endTime, int resultCount) {
        RmqMessageQuery query = new RmqMessageQuery();
        query.setQueryType(queryType);
        query.setTopic(topic);
        query.setMsgId(msgId);
        query.setTag(tag);
        query.setMessageKey(key);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setResultCount(resultCount);
        query.setQueriedAt(LocalDateTime.now());
        query.setQueriedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        messageQueryMapper.insert(query);
        log.debug("Message query recorded: type={} topic={}", queryType, topic);
    }

    public void recordTraceQuery(String msgId, String topic, int nodeCount, int consumerCount) {
        RmqTraceQuery query = new RmqTraceQuery();
        query.setMsgId(msgId);
        query.setTopic(topic);
        query.setNodeCount(nodeCount);
        query.setConsumerCount(consumerCount);
        query.setQueriedAt(LocalDateTime.now());
        query.setQueriedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        traceQueryMapper.insert(query);
        log.debug("Trace query recorded: msgId={} topic={}", msgId, topic);
    }
}
