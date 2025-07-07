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

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.utils.ThreadUtils;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ConsumerGroupRollBackStat;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.QueueStatInfo;
import org.apache.rocketmq.dashboard.model.TopicConsumerInfo;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.model.request.DeleteSubGroupRequest;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.dashboard.service.AbstractCommonService;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.client.ProxyAdmin;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ConsumerServiceImpl extends AbstractCommonService implements ConsumerService, InitializingBean, DisposableBean {
    private Logger logger = LoggerFactory.getLogger(ConsumerServiceImpl.class);

    @Resource
    protected ProxyAdmin proxyAdmin;
    @Resource
    private RMQConfigure configure;

    @Autowired
    private ClusterInfoService clusterInfoService;

    private volatile boolean isCacheBeingBuilt = false;

    private static final Set<String> SYSTEM_GROUP_SET = new HashSet<>();

    private ExecutorService executorService;

    private final List<GroupConsumeInfo> cacheConsumeInfoList = Collections.synchronizedList(new ArrayList<>());

    private final HashMap<String, List<String>> consumerGroupMap = Maps.newHashMap();

    @Override
    public void afterPropertiesSet() {
        Runtime runtime = Runtime.getRuntime();
        int corePoolSize = Math.max(10, runtime.availableProcessors() * 2);
        int maximumPoolSize = Math.max(20, runtime.availableProcessors() * 2);
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicLong threadIndex = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "QueryGroup_" + this.threadIndex.incrementAndGet());
            }
        };
        RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardOldestPolicy();
        this.executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(5000), threadFactory, handler);
    }

    @Override
    public void destroy() {
        ThreadUtils.shutdownGracefully(executorService, 10L, TimeUnit.SECONDS);
    }

    static {
        SYSTEM_GROUP_SET.add(MixAll.TOOLS_CONSUMER_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.FILTERSRV_CONSUMER_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.SELF_TEST_CONSUMER_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.ONS_HTTP_PROXY_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.CID_ONSAPI_PULL_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.CID_ONSAPI_PERMISSION_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.CID_ONSAPI_OWNER_GROUP);
        SYSTEM_GROUP_SET.add(MixAll.CID_SYS_RMQ_TRANS);
    }

    @Override
    public List<GroupConsumeInfo> queryGroupList(boolean skipSysGroup, String address) {
        if (isCacheBeingBuilt) {
            throw new RuntimeException("Cache is being built, please try again later");
        }

        synchronized (this) {
            if (cacheConsumeInfoList.isEmpty() && !isCacheBeingBuilt) {
                isCacheBeingBuilt = true;
                try {
                    makeGroupListCache();
                } finally {
                    isCacheBeingBuilt = false;
                }
            }
        }

        if (cacheConsumeInfoList.isEmpty()) {
            throw new RuntimeException("No consumer group information available");
        }

        List<GroupConsumeInfo> groupConsumeInfoList = new ArrayList<>(cacheConsumeInfoList);

        if (!skipSysGroup) {
            groupConsumeInfoList.stream().map(group -> {
                if (SYSTEM_GROUP_SET.contains(group.getGroup())) {
                    group.setGroup(String.format("%s%s", "%SYS%", group.getGroup()));
                }
                return group;
            }).collect(Collectors.toList());
        }
        Collections.sort(groupConsumeInfoList);
        return groupConsumeInfoList;
    }


    public void makeGroupListCache() {
        SubscriptionGroupWrapper subscriptionGroupWrapper = null;
        try {
            ClusterInfo clusterInfo = clusterInfoService.get();
            for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
                subscriptionGroupWrapper = mqAdminExt.getAllSubscriptionGroup(brokerData.selectBrokerAddr(), 30000L);
                for (String groupName : subscriptionGroupWrapper.getSubscriptionGroupTable().keySet()) {
                    if (!consumerGroupMap.containsKey(groupName)) {
                        consumerGroupMap.putIfAbsent(groupName, new ArrayList<>());
                    }
                    List<String> addresses = consumerGroupMap.get(groupName);
                    addresses.add(brokerData.selectBrokerAddr());
                    consumerGroupMap.put(groupName, addresses);
                }
            }
        } catch (Exception err) {
            Throwables.throwIfUnchecked(err);
            throw new RuntimeException(err);
        }

        if (subscriptionGroupWrapper != null && subscriptionGroupWrapper.getSubscriptionGroupTable().isEmpty()) {
            logger.warn("No subscription group information available");
            isCacheBeingBuilt = false;
            return;
        }
        final ConcurrentMap<String, SubscriptionGroupConfig> subscriptionGroupTable = subscriptionGroupWrapper.getSubscriptionGroupTable();
        List<GroupConsumeInfo> groupConsumeInfoList = Collections.synchronizedList(Lists.newArrayList());
        CountDownLatch countDownLatch = new CountDownLatch(consumerGroupMap.size());
        for (Map.Entry<String, List<String>> entry : consumerGroupMap.entrySet()) {
            String consumerGroup = entry.getKey();
            executorService.submit(() -> {
                try {
                    GroupConsumeInfo consumeInfo = queryGroup(consumerGroup, "");
                    consumeInfo.setAddress(entry.getValue());
                    if (SYSTEM_GROUP_SET.contains(consumerGroup)) {
                        consumeInfo.setSubGroupType("SYSTEM");
                    } else {
                        try {
                            consumeInfo.setSubGroupType(subscriptionGroupTable.get(consumerGroup).isConsumeMessageOrderly() ? "FIFO" : "NORMAL");
                        } catch (NullPointerException e) {
                            logger.warn("SubscriptionGroupConfig not found for consumer group: {}", consumerGroup);
                            boolean isFifoType = examineSubscriptionGroupConfig(consumerGroup)
                                    .stream().map(ConsumerConfigInfo::getSubscriptionGroupConfig)
                                    .allMatch(SubscriptionGroupConfig::isConsumeMessageOrderly);
                            consumeInfo.setSubGroupType(isFifoType ? "FIFO" : "NORMAL");
                        }
                    }
                    consumeInfo.setUpdateTime(new Date());
                    groupConsumeInfoList.add(consumeInfo);
                } catch (Exception e) {
                    logger.error("queryGroup exception, consumerGroup: {}", consumerGroup, e);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interruption occurred while waiting for task completion", e);
        }
        logger.info("All consumer group query tasks have been completed");
        isCacheBeingBuilt = false;
        Collections.sort(groupConsumeInfoList);

        cacheConsumeInfoList.clear();
        cacheConsumeInfoList.addAll(groupConsumeInfoList);
    }

    @Override
    public GroupConsumeInfo queryGroup(String consumerGroup, String address) {
        GroupConsumeInfo groupConsumeInfo = new GroupConsumeInfo();
        try {
            ConsumeStats consumeStats = null;
            try {
                consumeStats = mqAdminExt.examineConsumeStats(consumerGroup);
            } catch (Exception e) {
                logger.warn("examineConsumeStats exception to consumerGroup {}, response [{}]", consumerGroup, e.getMessage());
            }
            if (consumeStats != null) {
                groupConsumeInfo.setConsumeTps((int) consumeStats.getConsumeTps());
                groupConsumeInfo.setDiffTotal(consumeStats.computeTotalDiff());
            }
            ConsumerConnection consumerConnection = null;
            try {
                if (StringUtils.isNotEmpty(address)) {
                    consumerConnection = proxyAdmin.examineConsumerConnectionInfo(address, consumerGroup);
                } else {
                    consumerConnection = mqAdminExt.examineConsumerConnectionInfo(consumerGroup);
                }
            } catch (Exception e) {
                logger.warn("examineConsumeStats exception to consumerGroup {}, response [{}]", consumerGroup, e.getMessage());
            }
            if (consumerConnection != null) {
                groupConsumeInfo.setCount(consumerConnection.getConnectionSet().size());
                groupConsumeInfo.setMessageModel(consumerConnection.getMessageModel());
                groupConsumeInfo.setConsumeType(consumerConnection.getConsumeType());
                groupConsumeInfo.setVersion(MQVersion.getVersionDesc(consumerConnection.computeMinVersion()));
            }
        } catch (Exception e) {
            logger.warn("examineConsumeStats or examineConsumerConnectionInfo exception, "
                    + consumerGroup, e);
        }
        groupConsumeInfo.setGroup(consumerGroup);
        return groupConsumeInfo;
    }

    @Override
    public List<TopicConsumerInfo> queryConsumeStatsListByGroupName(String groupName, String address) {
        List<ConsumeStats> consumeStatses = new ArrayList<>();
        String topic = null;
        try {
            String[] addresses = address.split(",");
            for (String addr : addresses) {
                consumeStatses.add(mqAdminExt.examineConsumeStats(addr, groupName, null, 3000));
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        List<TopicConsumerInfo> res = new ArrayList<>();
        consumeStatses.forEach(consumeStats -> {
            if (consumeStats != null && consumeStats.getOffsetTable() != null && !consumeStats.getOffsetTable().isEmpty()) {
                res.addAll(toTopicConsumerInfoList(topic, consumeStats, groupName));
            }
        });
        return res;
    }

    @Override
    public List<TopicConsumerInfo> queryConsumeStatsList(final String topic, String groupName) {
        ConsumeStats consumeStats = null;
        try {
            consumeStats = mqAdminExt.examineConsumeStats(groupName, topic);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return toTopicConsumerInfoList(topic, consumeStats, groupName);
    }

    private List<TopicConsumerInfo> toTopicConsumerInfoList(String topic, ConsumeStats consumeStats, String groupName) {
        List<MessageQueue> mqList = Lists.newArrayList(Iterables.filter(consumeStats.getOffsetTable().keySet(), new Predicate<MessageQueue>() {
            @Override
            public boolean apply(MessageQueue o) {
                return StringUtils.isBlank(topic) || o.getTopic().equals(topic);
            }
        }));
        Collections.sort(mqList);
        List<TopicConsumerInfo> topicConsumerInfoList = Lists.newArrayList();
        TopicConsumerInfo nowTopicConsumerInfo = null;
        Map<MessageQueue, String> messageQueueClientMap = getClientConnection(groupName);
        for (MessageQueue mq : mqList) {
            if (nowTopicConsumerInfo == null || (!StringUtils.equals(mq.getTopic(), nowTopicConsumerInfo.getTopic()))) {
                nowTopicConsumerInfo = new TopicConsumerInfo(mq.getTopic());
                topicConsumerInfoList.add(nowTopicConsumerInfo);
            }
            QueueStatInfo queueStatInfo = QueueStatInfo.fromOffsetTableEntry(mq, consumeStats.getOffsetTable().get(mq));
            queueStatInfo.setClientInfo(messageQueueClientMap.get(mq));
            nowTopicConsumerInfo.appendQueueStatInfo(queueStatInfo);
        }
        return topicConsumerInfoList;
    }

    private Map<MessageQueue, String> getClientConnection(String groupName) {
        Map<MessageQueue, String> results = Maps.newHashMap();
        try {
            ConsumerConnection consumerConnection = mqAdminExt.examineConsumerConnectionInfo(groupName);
            for (Connection connection : consumerConnection.getConnectionSet()) {
                String clinetId = connection.getClientId();
                ConsumerRunningInfo consumerRunningInfo = mqAdminExt.getConsumerRunningInfo(groupName, clinetId, false);
                for (MessageQueue messageQueue : consumerRunningInfo.getMqTable().keySet()) {
//                    results.put(messageQueue, clinetId + " " + connection.getClientAddr());
                    results.put(messageQueue, clinetId);
                }
            }
        } catch (Exception err) {
            logger.error("op=getClientConnection_error", err);
        }
        return results;
    }

    @Override
    public Map<String /*groupName*/, TopicConsumerInfo> queryConsumeStatsListByTopicName(String topic) {
        Map<String, TopicConsumerInfo> group2ConsumerInfoMap = Maps.newHashMap();
        try {
            GroupList groupList = mqAdminExt.queryTopicConsumeByWho(topic);
            for (String group : groupList.getGroupList()) {
                List<TopicConsumerInfo> topicConsumerInfoList = null;
                try {
                    topicConsumerInfoList = queryConsumeStatsList(topic, group);
                } catch (Exception ignore) {
                }
                group2ConsumerInfoMap.put(group, CollectionUtils.isEmpty(topicConsumerInfoList) ? new TopicConsumerInfo(topic) : topicConsumerInfoList.get(0));
            }
            return group2ConsumerInfoMap;
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, ConsumerGroupRollBackStat> resetOffset(ResetOffsetRequest resetOffsetRequest) {
        Map<String, ConsumerGroupRollBackStat> groupRollbackStats = Maps.newHashMap();
        for (String consumerGroup : resetOffsetRequest.getConsumerGroupList()) {
            try {
                Map<MessageQueue, Long> rollbackStatsMap =
                        mqAdminExt.resetOffsetByTimestamp(resetOffsetRequest.getTopic(), consumerGroup, resetOffsetRequest.getResetTime(), resetOffsetRequest.isForce());
                ConsumerGroupRollBackStat consumerGroupRollBackStat = new ConsumerGroupRollBackStat(true);
                List<RollbackStats> rollbackStatsList = consumerGroupRollBackStat.getRollbackStatsList();
                for (Map.Entry<MessageQueue, Long> rollbackStatsEntty : rollbackStatsMap.entrySet()) {
                    RollbackStats rollbackStats = new RollbackStats();
                    rollbackStats.setRollbackOffset(rollbackStatsEntty.getValue());
                    rollbackStats.setQueueId(rollbackStatsEntty.getKey().getQueueId());
                    rollbackStats.setBrokerName(rollbackStatsEntty.getKey().getBrokerName());
                    rollbackStatsList.add(rollbackStats);
                }
                groupRollbackStats.put(consumerGroup, consumerGroupRollBackStat);
            } catch (MQClientException e) {
                if (ResponseCode.CONSUMER_NOT_ONLINE == e.getResponseCode()) {
                    try {
                        ConsumerGroupRollBackStat consumerGroupRollBackStat = new ConsumerGroupRollBackStat(true);
                        List<RollbackStats> rollbackStatsList = mqAdminExt.resetOffsetByTimestampOld(consumerGroup, resetOffsetRequest.getTopic(), resetOffsetRequest.getResetTime(), true);
                        consumerGroupRollBackStat.setRollbackStatsList(rollbackStatsList);
                        groupRollbackStats.put(consumerGroup, consumerGroupRollBackStat);
                        continue;
                    } catch (Exception err) {
                        logger.error("op=resetOffset_which_not_online_error", err);
                    }
                } else {
                    logger.error("op=resetOffset_error", e);
                }
                groupRollbackStats.put(consumerGroup, new ConsumerGroupRollBackStat(false, e.getMessage()));
            } catch (Exception e) {
                logger.error("op=resetOffset_error", e);
                groupRollbackStats.put(consumerGroup, new ConsumerGroupRollBackStat(false, e.getMessage()));
            }
        }
        return groupRollbackStats;
    }

    @Override
    public List<ConsumerConfigInfo> examineSubscriptionGroupConfig(String group) {
        List<ConsumerConfigInfo> consumerConfigInfoList = Lists.newArrayList();
        try {
            ClusterInfo clusterInfo = clusterInfoService.get();
            for (String brokerName : clusterInfo.getBrokerAddrTable().keySet()) { //foreach brokerName
                String brokerAddress = clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr();
                SubscriptionGroupConfig subscriptionGroupConfig = null;
                try {
                    subscriptionGroupConfig = mqAdminExt.examineSubscriptionGroupConfig(brokerAddress, group);
                } catch (Exception e) {
                    logger.warn("op=examineSubscriptionGroupConfig_error brokerName={} group={}", brokerName, group);
                }
                if (subscriptionGroupConfig == null) {
                    continue;
                }
                consumerConfigInfoList.add(new ConsumerConfigInfo(Lists.newArrayList(brokerName), subscriptionGroupConfig));
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return consumerConfigInfoList;
    }

    @Override
    public boolean deleteSubGroup(DeleteSubGroupRequest deleteSubGroupRequest) {
        Set<String> brokerSet = this.fetchBrokerNameSetBySubscriptionGroup(deleteSubGroupRequest.getGroupName());
        List<String> brokerList = deleteSubGroupRequest.getBrokerNameList();
        boolean deleteInNsFlag = false;
        // If the list of brokers passed in by the request contains the list of brokers that the consumer is in, delete RETRY and DLQ topic in namesrv
        if (brokerList.containsAll(brokerSet)) {
            deleteInNsFlag = true;
        }
        try {
            ClusterInfo clusterInfo = clusterInfoService.get();
            for (String brokerName : deleteSubGroupRequest.getBrokerNameList()) {
                logger.info("addr={} groupName={}", clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), deleteSubGroupRequest.getGroupName());
                mqAdminExt.deleteSubscriptionGroup(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), deleteSubGroupRequest.getGroupName(), true);
                // Delete %RETRY%+Group and %DLQ%+Group in broker and namesrv
                deleteResources(MixAll.RETRY_GROUP_TOPIC_PREFIX + deleteSubGroupRequest.getGroupName(), brokerName, clusterInfo, deleteInNsFlag);
                deleteResources(MixAll.DLQ_GROUP_TOPIC_PREFIX + deleteSubGroupRequest.getGroupName(), brokerName, clusterInfo, deleteInNsFlag);
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return true;
    }

    private void deleteResources(String topic, String brokerName, ClusterInfo clusterInfo, boolean deleteInNsFlag) throws Exception {
        mqAdminExt.deleteTopicInBroker(Sets.newHashSet(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr()), topic);
        Set<String> nameServerSet = null;
        if (StringUtils.isNotBlank(configure.getNamesrvAddr())) {
            String[] ns = configure.getNamesrvAddr().split(";");
            nameServerSet = new HashSet<>(Arrays.asList(ns));
        }
        if (deleteInNsFlag) {
            mqAdminExt.deleteTopicInNameServer(nameServerSet, topic);
        }
    }

    @Override
    public boolean createAndUpdateSubscriptionGroupConfig(ConsumerConfigInfo consumerConfigInfo) {
        try {
            ClusterInfo clusterInfo = clusterInfoService.get();
            for (String brokerName : changeToBrokerNameSet(clusterInfo.getClusterAddrTable(),
                    consumerConfigInfo.getClusterNameList(), consumerConfigInfo.getBrokerNameList())) {
                mqAdminExt.createAndUpdateSubscriptionGroupConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), consumerConfigInfo.getSubscriptionGroupConfig());
            }
        } catch (Exception err) {
            Throwables.throwIfUnchecked(err);
            throw new RuntimeException(err);
        }
        return true;
    }

    @Override
    public Set<String> fetchBrokerNameSetBySubscriptionGroup(String group) {
        Set<String> brokerNameSet = Sets.newHashSet();
        try {
            List<ConsumerConfigInfo> consumerConfigInfoList = examineSubscriptionGroupConfig(group);
            for (ConsumerConfigInfo consumerConfigInfo : consumerConfigInfoList) {
                brokerNameSet.addAll(consumerConfigInfo.getBrokerNameList());
            }
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return brokerNameSet;

    }

    @Override
    public ConsumerConnection getConsumerConnection(String consumerGroup, String address) {
        try {
            String[] addresses = address.split(",");
            String addr = addresses[0];
            return mqAdminExt.examineConsumerConnectionInfo(consumerGroup, addr);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public ConsumerRunningInfo getConsumerRunningInfo(String consumerGroup, String clientId, boolean jstack) {
        try {
            return mqAdminExt.getConsumerRunningInfo(consumerGroup, clientId, jstack);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public GroupConsumeInfo refreshGroup(String address, String consumerGroup) {

        if (isCacheBeingBuilt || cacheConsumeInfoList.isEmpty()) {
            throw new RuntimeException("Cache is being built or empty, please try again later");
        }
        synchronized (cacheConsumeInfoList) {
            for (int i = 0; i < cacheConsumeInfoList.size(); i++) {
                GroupConsumeInfo groupConsumeInfo = cacheConsumeInfoList.get(i);
                if (groupConsumeInfo.getGroup().equals(consumerGroup)) {
                    GroupConsumeInfo updatedInfo = queryGroup(consumerGroup, "");
                    updatedInfo.setUpdateTime(new Date());
                    updatedInfo.setGroup(consumerGroup);
                    updatedInfo.setAddress(consumerGroupMap.get(consumerGroup));
                    cacheConsumeInfoList.set(i, updatedInfo);
                    return updatedInfo;
                }
            }
        }
        throw new RuntimeException("No consumer group information available");
    }

    @Override
    public List<GroupConsumeInfo> refreshAllGroup(String address) {
        cacheConsumeInfoList.clear();
        consumerGroupMap.clear();
        return queryGroupList(false, address);
    }
}
