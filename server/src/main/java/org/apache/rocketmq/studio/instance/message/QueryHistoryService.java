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
package org.apache.rocketmq.studio.instance.message;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.persistence.entity.RmqMessageQuery;
import org.apache.rocketmq.studio.persistence.entity.RmqTraceQuery;
import org.apache.rocketmq.studio.persistence.mapper.RmqMessageQueryMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTraceQueryMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
public class QueryHistoryService {

    private final RmqMessageQueryMapper messageQueryMapper;
    private final RmqTraceQueryMapper traceQueryMapper;
    private final QueryHistoryProperties properties;
    private final Clock clock;

    @Autowired
    public QueryHistoryService(RmqMessageQueryMapper messageQueryMapper,
                               RmqTraceQueryMapper traceQueryMapper,
                               QueryHistoryProperties properties) {
        this(messageQueryMapper, traceQueryMapper, properties, Clock.systemUTC());
    }

    QueryHistoryService(RmqMessageQueryMapper messageQueryMapper,
                        RmqTraceQueryMapper traceQueryMapper,
                        QueryHistoryProperties properties,
                        Clock clock) {
        this.messageQueryMapper = messageQueryMapper;
        this.traceQueryMapper = traceQueryMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordMessageQuery(String clusterId, String queryType, String topic, String msgId,
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
        query.setClusterId(clusterId);
        query.setQueriedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        query.setQueriedAt(LocalDateTime.now(clock));
        messageQueryMapper.insert(query);
        log.debug("Message query recorded: clusterId={} type={} topic={}", clusterId, queryType, topic);
    }

    public void recordTraceQuery(String clusterId, String msgId, String topic, int nodeCount, int consumerCount) {
        RmqTraceQuery query = new RmqTraceQuery();
        query.setMsgId(msgId);
        query.setTopic(topic);
        query.setNodeCount(nodeCount);
        query.setConsumerCount(consumerCount);
        query.setClusterId(clusterId);
        query.setQueriedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        query.setQueriedAt(LocalDateTime.now(clock));
        traceQueryMapper.insert(query);
        log.debug("Trace query recorded: clusterId={} msgId={} topic={}", clusterId, msgId, topic);
    }

    @Scheduled(fixedDelayString = "${studio.query-history.cleanup-interval:PT24H}")
    public void purgeExpiredQueries() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        deleteExpiredMessageQueries(cutoff);
        deleteExpiredTraceQueries(cutoff);
    }

    private void deleteExpiredMessageQueries(LocalDateTime cutoff) {
        try {
            int deleted = messageQueryMapper.delete(Wrappers.<RmqMessageQuery>query()
                    .lt("queried_at", cutoff));
            log.debug("Purged {} expired message query records", deleted);
        } catch (RuntimeException e) {
            log.warn("Failed to purge expired message query records: {}", e.getMessage());
        }
    }

    private void deleteExpiredTraceQueries(LocalDateTime cutoff) {
        try {
            int deleted = traceQueryMapper.delete(Wrappers.<RmqTraceQuery>query()
                    .lt("queried_at", cutoff));
            log.debug("Purged {} expired trace query records", deleted);
        } catch (RuntimeException e) {
            log.warn("Failed to purge expired trace query records: {}", e.getMessage());
        }
    }
}
