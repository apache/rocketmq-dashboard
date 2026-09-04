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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.persistence.entity.RmqMessageQuery;
import org.apache.rocketmq.studio.persistence.entity.RmqTraceQuery;
import org.apache.rocketmq.studio.persistence.mapper.RmqMessageQueryMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTraceQueryMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QueryHistoryService {

    private final RmqMessageQueryMapper messageQueryMapper;
    private final RmqTraceQueryMapper traceQueryMapper;
    private final QueryHistoryProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    private static final int MAX_SNAPSHOT_RECORDS = 500;

    @Autowired
    public QueryHistoryService(RmqMessageQueryMapper messageQueryMapper,
                               RmqTraceQueryMapper traceQueryMapper,
                               QueryHistoryProperties properties,
                               ObjectMapper objectMapper) {
        this(messageQueryMapper, traceQueryMapper, properties, Clock.systemUTC(), objectMapper);
    }

    QueryHistoryService(RmqMessageQueryMapper messageQueryMapper,
                        RmqTraceQueryMapper traceQueryMapper,
                        QueryHistoryProperties properties,
                        Clock clock,
                        ObjectMapper objectMapper) {
        this.messageQueryMapper = messageQueryMapper;
        this.traceQueryMapper = traceQueryMapper;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public void recordMessageQuery(String clusterId, String queryType, String topic, String msgId,
                                   String tag, String key, Long startTime,
                                   Long endTime, int resultCount, String resultSnapshot) {
        RmqMessageQuery query = new RmqMessageQuery();
        query.setQueryType(queryType);
        query.setTopic(topic);
        query.setMsgId(msgId);
        query.setTag(tag);
        query.setMessageKey(key);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setResultCount(resultCount);
        query.setResultSnapshot(resultSnapshot);
        query.setClusterId(clusterId);
        query.setQueriedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        LocalDateTime now = LocalDateTime.now(clock);
        query.setGmtCreate(now);
        query.setGmtModified(now);
        messageQueryMapper.insert(query);
        log.debug("Message query recorded: clusterId={} type={} topic={}", clusterId, queryType, topic);
    }

    /**
     * Builds a JSON snapshot of query results, excluding message body and properties to save storage.
     */
    public String buildResultSnapshot(List<MessageRecordVO> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> snapshots = results.stream()
                    .limit(MAX_SNAPSHOT_RECORDS)
                    .map(r -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("msgId", r.getMsgId() == null ? "" : r.getMsgId());
                        m.put("topic", r.getTopic() == null ? "" : r.getTopic());
                        m.put("tag", r.getTag() == null ? "" : r.getTag());
                        m.put("key", r.getKey() == null ? "" : r.getKey());
                        m.put("brokerName", r.getBrokerName() == null ? "" : r.getBrokerName());
                        m.put("queueId", r.getQueueId() == null ? 0 : r.getQueueId());
                        m.put("queueOffset", r.getQueueOffset() == null ? 0L : r.getQueueOffset());
                        m.put("storeTime", r.getStoreTime());
                        m.put("bornHost", r.getBornHost() == null ? "" : r.getBornHost());
                        m.put("storeHost", r.getStoreHost() == null ? "" : r.getStoreHost());
                        m.put("size", r.getSize());
                        return m;
                    }).toList();
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize result snapshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the stored result snapshot for a given history record.
     */
    public List<MessageRecordVO> getMessageQueryResults(long id) {
        RmqMessageQuery query = messageQueryMapper.selectById(id);
        if (query == null) {
            throw new org.apache.rocketmq.studio.common.exception.BusinessException(404, "Query history record not found");
        }
        String snapshot = query.getResultSnapshot();
        if (!StringUtils.hasText(snapshot)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(snapshot,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MessageRecordVO.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize result snapshot for id={}: {}", id, e.getMessage());
            return List.of();
        }
    }

    public void recordTraceQuery(String clusterId, String msgId, String topic, int nodeCount, int consumerCount) {
        RmqTraceQuery query = new RmqTraceQuery();
        query.setMsgId(msgId);
        query.setTopic(topic);
        query.setNodeCount(nodeCount);
        query.setConsumerCount(consumerCount);
        query.setClusterId(clusterId);
        query.setQueriedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        LocalDateTime now = LocalDateTime.now(clock);
        query.setGmtCreate(now);
        query.setGmtModified(now);
        traceQueryMapper.insert(query);
        log.debug("Trace query recorded: clusterId={} msgId={} topic={}", clusterId, msgId, topic);
    }

    public PageResult<MessageQueryHistoryVO> listMessageQueries(String clusterId, String queryType,
                                                                 String search, int page, int pageSize) {
        String pattern = escapeLike(search);
        String queriedBy = AuthenticatedUserContext.currentUsernameOrSystem();
        QueryWrapper<RmqMessageQuery> query = new QueryWrapper<RmqMessageQuery>()
                .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                .eq("queried_by", queriedBy)
                .eq(StringUtils.hasText(queryType), "query_type", queryType)
                .and(StringUtils.hasText(search), nested -> nested
                        .like("topic", pattern)
                        .or().like("msg_id", pattern)
                        .or().like("message_key", pattern)
                        .or().like("queried_by", pattern))
                .orderByDesc("gmt_create", "id");
        Page<RmqMessageQuery> result = messageQueryMapper.selectPage(new Page<>(page, pageSize), query);
        List<MessageQueryHistoryVO> items = result.getRecords().stream()
                .map(QueryHistoryService::toMessageHistory).toList();
        return PageResult.of(items, result.getTotal(), page, pageSize);
    }

    public PageResult<TraceQueryHistoryVO> listTraceQueries(String clusterId, String search,
                                                             int page, int pageSize) {
        String pattern = escapeLike(search);
        String queriedBy = AuthenticatedUserContext.currentUsernameOrSystem();
        QueryWrapper<RmqTraceQuery> query = new QueryWrapper<RmqTraceQuery>()
                .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                .eq("queried_by", queriedBy)
                .and(StringUtils.hasText(search), nested -> nested
                        .like("topic", pattern)
                        .or().like("msg_id", pattern)
                        .or().like("queried_by", pattern))
                .orderByDesc("gmt_create", "id");
        Page<RmqTraceQuery> result = traceQueryMapper.selectPage(new Page<>(page, pageSize), query);
        List<TraceQueryHistoryVO> items = result.getRecords().stream()
                .map(QueryHistoryService::toTraceHistory).toList();
        return PageResult.of(items, result.getTotal(), page, pageSize);
    }

    public QueryHistorySummaryVO summarize(String clusterId) {
        String queriedBy = AuthenticatedUserContext.currentUsernameOrSystem();
        QueryWrapper<RmqMessageQuery> messageFilter = new QueryWrapper<RmqMessageQuery>()
                .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                .eq("queried_by", queriedBy);
        QueryWrapper<RmqTraceQuery> traceFilter = new QueryWrapper<RmqTraceQuery>()
                .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                .eq("queried_by", queriedBy);
        long messageCount = messageQueryMapper.selectCount(messageFilter);
        long traceCount = traceQueryMapper.selectCount(traceFilter);
        RmqMessageQuery latestMessage = messageQueryMapper.selectOne(
                new QueryWrapper<RmqMessageQuery>()
                        .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                        .eq("queried_by", queriedBy)
                        .orderByDesc("gmt_create", "id").last("LIMIT 1"));
        RmqTraceQuery latestTrace = traceQueryMapper.selectOne(
                new QueryWrapper<RmqTraceQuery>()
                        .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                        .eq("queried_by", queriedBy)
                        .orderByDesc("gmt_create", "id").last("LIMIT 1"));
        LocalDateTime latest = latestOf(
                latestMessage == null ? null : latestMessage.getGmtCreate(),
                latestTrace == null ? null : latestTrace.getGmtCreate());
        return QueryHistorySummaryVO.builder()
                .messageQueries(messageCount)
                .traceQueries(traceCount)
                .latestQueryAt(latest)
                .build();
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
                    .lt("gmt_create", cutoff));
            log.debug("Purged {} expired message query records", deleted);
        } catch (RuntimeException e) {
            log.warn("Failed to purge expired message query records: {}", e.getMessage());
        }
    }

    private void deleteExpiredTraceQueries(LocalDateTime cutoff) {
        try {
            int deleted = traceQueryMapper.delete(Wrappers.<RmqTraceQuery>query()
                    .lt("gmt_create", cutoff));
            log.debug("Purged {} expired trace query records", deleted);
        } catch (RuntimeException e) {
            log.warn("Failed to purge expired trace query records: {}", e.getMessage());
        }
    }

    private static LocalDateTime latestOf(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private static MessageQueryHistoryVO toMessageHistory(RmqMessageQuery query) {
        return MessageQueryHistoryVO.builder()
                .id(query.getId()).queryType(query.getQueryType()).topic(query.getTopic())
                .msgId(query.getMsgId()).tag(query.getTag()).messageKey(query.getMessageKey())
                .startTime(query.getStartTime()).endTime(query.getEndTime())
                .resultCount(query.getResultCount() == null ? 0 : query.getResultCount())
                .clusterId(query.getClusterId()).queriedBy(query.getQueriedBy())
                .queriedAt(query.getGmtCreate()).build();
    }

    private static TraceQueryHistoryVO toTraceHistory(RmqTraceQuery query) {
        return TraceQueryHistoryVO.builder()
                .id(query.getId()).msgId(query.getMsgId()).topic(query.getTopic())
                .nodeCount(query.getNodeCount() == null ? 0 : query.getNodeCount())
                .consumerCount(query.getConsumerCount() == null ? 0 : query.getConsumerCount())
                .clusterId(query.getClusterId()).queriedBy(query.getQueriedBy())
                .queriedAt(query.getGmtCreate()).build();
    }

    /**
     * Escapes LIKE wildcards so user-supplied search terms match literally instead of being
     * interpreted as {@code %}/{@code _} patterns.
     */
    private static String escapeLike(String search) {
        if (!StringUtils.hasText(search)) {
            return search;
        }
        return search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
