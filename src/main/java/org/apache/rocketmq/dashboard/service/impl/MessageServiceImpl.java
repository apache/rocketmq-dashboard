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


import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.utils.NetworkUtil;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessagePageTask;
import org.apache.rocketmq.dashboard.model.MessageQueryByPage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.QueueOffsetInfo;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.apache.rocketmq.tools.admin.api.TrackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private AutoCloseConsumerWrapper autoCloseConsumerWrapper;

    private Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);

    private static final Cache<String, List<QueueOffsetInfo>> CACHE = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .build();

    @Autowired
    private RMQConfigure configure;
    /**
     * @see org.apache.rocketmq.store.config.MessageStoreConfig maxMsgsNumBatch = 64;
     * @see org.apache.rocketmq.store.index.IndexService maxNum = Math.min(maxNum, this.defaultMessageStore.getMessageStoreConfig().getMaxMsgsNumBatch());
     */
    private final static int QUERY_MESSAGE_MAX_NUM = 64;
    @Resource
    private MQAdminExt mqAdminExt;

    @Override
    public Pair<MessageView, List<MessageTrack>> viewMessage(String subject, final String msgId) {
        try {

            MessageExt messageExt = mqAdminExt.viewMessage(subject, msgId);
            List<MessageTrack> messageTrackList = messageTrackDetail(messageExt);
            return new Pair<>(MessageView.fromMessageExt(messageExt), messageTrackList);
        } catch (Exception e) {
            throw new ServiceException(-1, String.format("Failed to query message by Id: %s", msgId));
        }
    }

    @Override
    public List<MessageView> queryMessageByTopicAndKey(String topic, String key) {
        try {
            return Lists.transform(mqAdminExt.queryMessage(topic, key, QUERY_MESSAGE_MAX_NUM, 0, System.currentTimeMillis()).getMessageList(), new Function<MessageExt, MessageView>() {
                @Override
                public MessageView apply(MessageExt messageExt) {
                    return MessageView.fromMessageExt(messageExt);
                }
            });
        } catch (Exception err) {
            if (err instanceof MQClientException) {
                throw new ServiceException(-1, ((MQClientException) err).getErrorMessage());
            }
            Throwables.throwIfUnchecked(err);
            throw new RuntimeException(err);
        }
    }

    @Override
    public List<MessageView> queryMessageByTopic(String topic, final long begin, final long end) {
        boolean isEnableAcl = !StringUtils.isEmpty(configure.getAccessKey()) && !StringUtils.isEmpty(configure.getSecretKey());
        RPCHook rpcHook = null;
        if (isEnableAcl) {
            rpcHook = new AclClientRPCHook(new SessionCredentials(configure.getAccessKey(), configure.getSecretKey()));
        }

        DefaultMQPullConsumer consumer = autoCloseConsumerWrapper.getConsumer(rpcHook, configure.isUseTLS());
        List<MessageView> messageViewList = Lists.newArrayList();
        try {
            String subExpression = "*";
            Set<MessageQueue> mqs = consumer.fetchSubscribeMessageQueues(topic);
            for (MessageQueue mq : mqs) {
                long minOffset = consumer.searchOffset(mq, begin);
                long maxOffset = consumer.searchOffset(mq, end);
                READQ:
                for (long offset = minOffset; offset <= maxOffset; ) {
                    try {
                        if (messageViewList.size() > 2000) {
                            break;
                        }
                        PullResult pullResult = consumer.pull(mq, subExpression, offset, 32);
                        offset = pullResult.getNextBeginOffset();
                        switch (pullResult.getPullStatus()) {
                            case FOUND:

                                List<MessageView> messageViewListByQuery = Lists.transform(pullResult.getMsgFoundList(), new Function<MessageExt, MessageView>() {
                                    @Override
                                    public MessageView apply(MessageExt messageExt) {
                                        messageExt.setBody(null);
                                        return MessageView.fromMessageExt(messageExt);
                                    }
                                });
                                List<MessageView> filteredList = Lists.newArrayList(Iterables.filter(messageViewListByQuery, new Predicate<MessageView>() {
                                    @Override
                                    public boolean apply(MessageView messageView) {
                                        if (messageView.getStoreTimestamp() < begin || messageView.getStoreTimestamp() > end) {
                                            logger.info("begin={} end={} time not in range {} {}", begin, end, messageView.getStoreTimestamp(), new Date(messageView.getStoreTimestamp()).toString());
                                        }
                                        return messageView.getStoreTimestamp() >= begin && messageView.getStoreTimestamp() <= end;
                                    }
                                }));
                                messageViewList.addAll(filteredList);
                                break;
                            case NO_MATCHED_MSG:
                            case NO_NEW_MSG:
                            case OFFSET_ILLEGAL:
                                break READQ;
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
            }
            Collections.sort(messageViewList, new Comparator<MessageView>() {
                @Override
                public int compare(MessageView o1, MessageView o2) {
                    if (o1.getStoreTimestamp() - o2.getStoreTimestamp() == 0) {
                        return 0;
                    }
                    return (o1.getStoreTimestamp() > o2.getStoreTimestamp()) ? -1 : 1;
                }
            });
            return messageViewList;
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<MessageTrack> messageTrackDetail(MessageExt msg) {
        List<MessageTrack> messageTracks;
        try {
            messageTracks = mqAdminExt.messageTrackDetail(msg);
        } catch (Exception e) {
            logger.error("op=messageTrackDetailError", e);
            return Collections.emptyList();
        }

        // Re-verify tracks marked as NOT_CONSUME_YET.
        //
        // Root cause: the underlying consumed() method in DefaultMQAdminExtImpl
        // requires an exact broker-address match between msg.getStoreHost() and
        // the broker's registered address.  When the broker registers with a
        // hostname (or when DNS resolution inside the dashboard JVM differs from
        // the broker), the address comparison silently fails and every message is
        // incorrectly reported as NOT_CONSUME_YET even though it has been consumed.
        //
        // The fallback below re-checks the consumer offset directly.  To avoid
        // cross-broker false positives in multi-broker clusters (where broker-a
        // and broker-b both have queueId=0 for the same topic with independent
        // offsets), we first resolve msg.getStoreHost() to a brokerName via the
        // topic route data, then only compare offsets for that brokerName.
        //
        // Conservative policy: if the brokerName cannot be determined (route info
        // unavailable or no address matches), the track remains NOT_CONSUME_YET —
        // we prefer preserving the original false negative over introducing a new
        // false positive.
        //
        // See: https://github.com/apache/rocketmq-dashboard/issues/380
        if (messageTracks != null && !messageTracks.isEmpty()) {
            // Cache ConsumeStats per consumer group within this request to avoid
            // duplicate examineConsumeStats RPCs when multiple tracks share a group.
            Map<String, ConsumeStats> statsCache = new HashMap<>();
            for (MessageTrack track : messageTracks) {
                if (track.getTrackType() == TrackType.NOT_CONSUME_YET) {
                    String group = track.getConsumerGroup();
                    ConsumeStats stats = statsCache.computeIfAbsent(group, g -> {
                        try {
                            return mqAdminExt.examineConsumeStats(g);
                        } catch (Exception e) {
                            logger.warn("op=examineConsumeStatsError, group={}", g, e);
                            return null;
                        }
                    });
                    if (stats != null && isConsumedByGroup(msg, stats)) {
                        track.setTrackType(TrackType.CONSUMED);
                    }
                }
            }
        }

        return messageTracks;
    }

    /**
     * Independently verify whether a message has been consumed by the given
     * consumer group, using pre-fetched {@link ConsumeStats}.
     *
     * <p>Unlike {@link org.apache.rocketmq.tools.admin.DefaultMQAdminExt#consumed},
     * this method does NOT rely on an exact broker-address string comparison.
     * Instead it:
     * <ol>
     *   <li>Resolves the message's store host to a brokerName via topic route
     *       data, performing a loose IP-level match against all registered
     *       broker addresses.</li>
     *   <li>Only compares consumer offsets for {@link MessageQueue}s whose
     *       brokerName, topic, and queueId all match, preventing cross-broker
     *       false positives in multi-broker deployments.</li>
     * </ol>
     *
     * @param msg   the message to verify
     * @param stats pre-fetched ConsumeStats for the consumer group
     * @return {@code true} only if the consumer offset for the matching broker
     *         has advanced past the message's queue offset; {@code false} if no
     *         matching broker is found or the offset has not been reached
     */
    private boolean isConsumedByGroup(MessageExt msg, ConsumeStats stats) {
        if (stats == null || stats.getOffsetTable() == null) {
            return false;
        }

        String targetBrokerName = resolveBrokerName(msg);
        if (targetBrokerName == null) {
            // Cannot determine which broker holds this message.
            // Conservative: do not upgrade, leave NOT_CONSUME_YET.
            return false;
        }

        for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
            MessageQueue mq = entry.getKey();
            if (mq.getBrokerName().equals(targetBrokerName)
                && mq.getTopic().equals(msg.getTopic())
                && mq.getQueueId() == msg.getQueueId()) {
                if (entry.getValue().getConsumerOffset() > msg.getQueueOffset()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Resolve the brokerName that owns the given message by matching
     * {@code msg.getStoreHost()} against the topic's route data.
     *
     * <p>The match is performed at the IP level: both the store host and the
     * registered broker addresses are normalised to their IP components before
     * comparison, so a broker that registers with a hostname whose DNS resolves
     * to the same IP as the store host will still match.
     *
     * @return the matching brokerName, or {@code null} if no match is found
     */
    private String resolveBrokerName(MessageExt msg) {
        try {
            TopicRouteData routeData = mqAdminExt.examineTopicRouteInfo(msg.getTopic());
            if (routeData == null || routeData.getBrokerDatas() == null) {
                return null;
            }

            List<BrokerData> brokerDatas = routeData.getBrokerDatas();

            // Short-circuit: single-broker deployment — no address matching needed.
            // This covers the most common deployment (dev / test / small-scale prod)
            // and is immune to DNS/hostname/NAT mismatches that would otherwise
            // cause the IP-level match below to fail silently.
            if (brokerDatas.size() == 1) {
                return brokerDatas.get(0).getBrokerName();
            }

            String storeHostIp = NetworkUtil.socketAddress2String(msg.getStoreHost()).split(":")[0];

            for (BrokerData brokerData : brokerDatas) {
                if (brokerData.getBrokerAddrs() == null) {
                    continue;
                }
                for (String brokerAddr : brokerData.getBrokerAddrs().values()) {
                    String brokerIp = NetworkUtil.convert2IpString(brokerAddr).split(":")[0];
                    if (storeHostIp.equals(brokerIp)) {
                        return brokerData.getBrokerName();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("op=resolveBrokerNameError, topic={}", msg.getTopic(), e);
        }
        return null;
    }


    @Override
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String topic, String msgId, String consumerGroup,
                                                               String clientId) {
        if (StringUtils.isNotBlank(clientId)) {
            try {
                return mqAdminExt.consumeMessageDirectly(consumerGroup, clientId, topic, msgId);
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }

        try {
            ConsumerConnection consumerConnection = mqAdminExt.examineConsumerConnectionInfo(consumerGroup);
            for (Connection connection : consumerConnection.getConnectionSet()) {
                if (StringUtils.isBlank(connection.getClientId())) {
                    continue;
                }
                logger.info("clientId={}", connection.getClientId());
                return mqAdminExt.consumeMessageDirectly(consumerGroup, connection.getClientId(), topic, msgId);
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("NO CONSUMER");

    }

    @Override
    public MessagePage queryMessageByPage(MessageQuery query) {
        MessageQueryByPage queryByPage = new MessageQueryByPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getTopic(),
                query.getBegin(),
                query.getEnd());

        List<QueueOffsetInfo> queueOffsetInfos = CACHE.getIfPresent(query.getTaskId());

        if (queueOffsetInfos == null) {
            query.setPageNum(1);
            MessagePageTask task = this.queryFirstMessagePage(queryByPage);
            String taskId = MessageClientIDSetter.createUniqID();
            CACHE.put(taskId, task.getQueueOffsetInfos());

            return new MessagePage(task.getPage(), taskId);
        }
        Page<MessageView> messageViews = queryMessageByTaskPage(queryByPage, queueOffsetInfos);
        return new MessagePage(messageViews, query.getTaskId());

    }

    private MessagePageTask queryFirstMessagePage(MessageQueryByPage query) {
        boolean isEnableAcl = !StringUtils.isEmpty(configure.getAccessKey()) && !StringUtils.isEmpty(configure.getSecretKey());
        RPCHook rpcHook = null;
        if (isEnableAcl) {
            rpcHook = new AclClientRPCHook(new SessionCredentials(configure.getAccessKey(), configure.getSecretKey()));
        }

        DefaultMQPullConsumer consumer = autoCloseConsumerWrapper.getConsumer(rpcHook, configure.isUseTLS());

        long total = 0;
        List<QueueOffsetInfo> queueOffsetInfos = new ArrayList<>();

        List<MessageView> messageViews = new ArrayList<>();

        try {
            Collection<MessageQueue> messageQueues = consumer.fetchSubscribeMessageQueues(query.getTopic());
            int idx = 0;
            for (MessageQueue messageQueue : messageQueues) {
                Long minOffset = consumer.searchOffset(messageQueue, query.getBegin());
                Long maxOffset = consumer.searchOffset(messageQueue, query.getEnd());
                queueOffsetInfos.add(new QueueOffsetInfo(idx++, minOffset, maxOffset, minOffset, minOffset, messageQueue));
            }

            // check first offset has message
            // filter the begin time
            for (QueueOffsetInfo queueOffset : queueOffsetInfos) {
                Long start = queueOffset.getStart();
                boolean hasData = false;
                boolean hasIllegalOffset = true;
                while (hasIllegalOffset) {
                    PullResult pullResult = consumer.pull(queueOffset.getMessageQueues(), "*", start, 32);
                    if (pullResult.getPullStatus() == PullStatus.FOUND) {
                        hasData = true;
                        List<MessageExt> msgFoundList = pullResult.getMsgFoundList();
                        for (MessageExt messageExt : msgFoundList) {
                            if (messageExt.getStoreTimestamp() < query.getBegin()) {
                                start++;
                            } else {
                                hasIllegalOffset = false;
                                break;
                            }
                        }
                    } else {
                        hasIllegalOffset = false;
                    }
                }
                if (!hasData) {
                    queueOffset.setEnd(queueOffset.getStart());
                }
                queueOffset.setStart(start);
                queueOffset.setStartOffset(start);
                queueOffset.setEndOffset(start);
            }

            // filter the end time
            for (QueueOffsetInfo queueOffset : queueOffsetInfos) {
                if (queueOffset.getStart().equals(queueOffset.getEnd())) {
                    continue;
                }
                long end = queueOffset.getEnd();
                long pullOffset = end;
                int pullSize = 32;
                boolean hasIllegalOffset = true;
                while (hasIllegalOffset) {

                    if (pullOffset - pullSize > queueOffset.getStart()) {
                        pullOffset = pullOffset - pullSize;
                    } else {
                        pullOffset = queueOffset.getStartOffset();
                        pullSize = (int) (end - pullOffset);
                    }
                    PullResult pullResult = consumer.pull(queueOffset.getMessageQueues(), "*", pullOffset, pullSize);
                    if (pullResult.getPullStatus() == PullStatus.FOUND) {
                        List<MessageExt> msgFoundList = pullResult.getMsgFoundList();
                        for (int i = msgFoundList.size() - 1; i >= 0; i--) {
                            MessageExt messageExt = msgFoundList.get(i);
                            if (messageExt.getStoreTimestamp() > query.getEnd()) {
                                end--;
                            } else {
                                hasIllegalOffset = false;
                                break;
                            }
                        }
                    } else {
                        hasIllegalOffset = false;
                    }
                    if (pullOffset == queueOffset.getStartOffset()) {
                        break;
                    }
                }
                queueOffset.setEnd(end);
                total += queueOffset.getEnd() - queueOffset.getStart();
            }

            long pageSize = total > query.getPageSize() ? query.getPageSize() : total;


            // move startOffset
            int next = moveStartOffset(queueOffsetInfos, query);
            moveEndOffset(queueOffsetInfos, query, next);

            // find the first page of message
            for (QueueOffsetInfo queueOffsetInfo : queueOffsetInfos) {
                Long start = queueOffsetInfo.getStartOffset();
                Long end = queueOffsetInfo.getEndOffset();
                long size = Math.min(end - start, pageSize);
                if (size == 0) {
                    continue;
                }

                while (size > 0) {
                    PullResult pullResult = consumer.pull(queueOffsetInfo.getMessageQueues(), "*", start, 32);
                    if (pullResult.getPullStatus() == PullStatus.FOUND) {
                        List<MessageExt> poll = pullResult.getMsgFoundList();
                        if (poll.size() == 0) {
                            break;
                        }
                        List<MessageView> collect = poll.stream()
                                .map(MessageView::fromMessageExt).collect(Collectors.toList());

                        for (MessageView view : collect) {
                            if (size > 0) {
                                messageViews.add(view);
                                size--;
                            }
                        }
                    } else {
                        break;
                    }

                }
            }
            PageImpl<MessageView> page = new PageImpl<>(messageViews, query.page(), total);
            return new MessagePageTask(page, queueOffsetInfos);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    private Page<MessageView> queryMessageByTaskPage(MessageQueryByPage query, List<QueueOffsetInfo> queueOffsetInfos) {
        boolean isEnableAcl = !StringUtils.isEmpty(configure.getAccessKey()) && !StringUtils.isEmpty(configure.getSecretKey());
        RPCHook rpcHook = null;
        if (isEnableAcl) {
            rpcHook = new AclClientRPCHook(new SessionCredentials(configure.getAccessKey(), configure.getSecretKey()));
        }

        DefaultMQPullConsumer consumer = autoCloseConsumerWrapper.getConsumer(rpcHook, configure.isUseTLS());
        List<MessageView> messageViews = new ArrayList<>();

        long offset = query.getPageNum() * query.getPageSize();

        long total = 0;
        try {
            for (QueueOffsetInfo queueOffsetInfo : queueOffsetInfos) {
                long start = queueOffsetInfo.getStart();
                long end = queueOffsetInfo.getEnd();
                queueOffsetInfo.setStartOffset(start);
                queueOffsetInfo.setEndOffset(start);
                total += end - start;
            }
            if (total <= offset) {
                return Page.empty();
            }
            long pageSize = total - offset > query.getPageSize() ? query.getPageSize() : total - offset;

            int next = moveStartOffset(queueOffsetInfos, query);
            moveEndOffset(queueOffsetInfos, query, next);

            for (QueueOffsetInfo queueOffsetInfo : queueOffsetInfos) {
                Long start = queueOffsetInfo.getStartOffset();
                Long end = queueOffsetInfo.getEndOffset();
                long size = Math.min(end - start, pageSize);
                if (size == 0) {
                    continue;
                }

                while (size > 0) {
                    PullResult pullResult = consumer.pull(queueOffsetInfo.getMessageQueues(), "*", start, 32);
                    if (pullResult.getPullStatus() == PullStatus.FOUND) {
                        List<MessageExt> poll = pullResult.getMsgFoundList();
                        if (poll.size() == 0) {
                            break;
                        }
                        List<MessageView> collect = poll.stream()
                                .map(MessageView::fromMessageExt).collect(Collectors.toList());

                        for (MessageView view : collect) {
                            if (size > 0) {
                                messageViews.add(view);
                                size--;
                            }
                        }
                    } else {
                        break;
                    }

                }
            }
            return new PageImpl<>(messageViews, query.page(), total);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    private int moveStartOffset(List<QueueOffsetInfo> queueOffsets, MessageQueryByPage query) {
        int size = queueOffsets.size();
        int next = 0;
        long offset = query.getPageNum() * query.getPageSize();
        if (offset == 0) {
            return next;
        }
        // sort by queueOffset size
        List<QueueOffsetInfo> orderQueue = queueOffsets
                .stream()
                .sorted((o1, o2) -> {
                    long size1 = o1.getEnd() - o1.getStart();
                    long size2 = o2.getEnd() - o2.getStart();
                    if (size1 < size2) {
                        return -1;
                    } else if (size1 > size2) {
                        return 1;
                    }
                    return 0;
                }).collect(Collectors.toList());

        // Take the smallest one each time
        for (int i = 0; i < size && offset >= (size - i); i++) {
            long minSize = orderQueue.get(i).getEnd() - orderQueue.get(i).getStartOffset();
            if (minSize == 0) {
                continue;
            }
            long reduce = minSize * (size - i);
            if (reduce <= offset) {
                offset -= reduce;
                for (int j = i; j < size; j++) {
                    orderQueue.get(j).incStartOffset(minSize);
                }
            } else {
                long addOffset = offset / (size - i);
                offset -= addOffset * (size - i);
                if (addOffset != 0) {
                    for (int j = i; j < size; j++) {
                        orderQueue.get(j).incStartOffset(addOffset);
                    }
                }
            }
        }
        for (QueueOffsetInfo info : orderQueue) {
            QueueOffsetInfo queueOffsetInfo = queueOffsets.get(info.getIdx());
            queueOffsetInfo.setStartOffset(info.getStartOffset());
            queueOffsetInfo.setEndOffset(info.getEndOffset());
        }

        for (QueueOffsetInfo info : queueOffsets) {
            if (offset == 0) {
                break;
            }
            next = (next + 1) % size;
            if (info.getStartOffset() < info.getEnd()) {
                info.incStartOffset();
                --offset;
            }
        }
        return next;
    }

    private void moveEndOffset(List<QueueOffsetInfo> queueOffsets, MessageQueryByPage query, int next) {
        int size = queueOffsets.size();
        for (int j = 0; j < query.getPageSize(); j++) {
            QueueOffsetInfo nextQueueOffset = queueOffsets.get(next);
            next = (next + 1) % size;
            int start = next;
            while (nextQueueOffset.getEndOffset() >= nextQueueOffset.getEnd()) {
                nextQueueOffset = queueOffsets.get(next);
                next = (next + 1) % size;
                if (start == next) {
                    return;
                }
            }
            nextQueueOffset.incEndOffset();
        }
    }

//    public DefaultMQPullConsumer buildDefaultMQPullConsumer(RPCHook rpcHook, boolean useTLS) {
//        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer(MixAll.TOOLS_CONSUMER_GROUP, rpcHook);
//        consumer.setUseTLS(useTLS);
//        return consumer;
//    }
}
