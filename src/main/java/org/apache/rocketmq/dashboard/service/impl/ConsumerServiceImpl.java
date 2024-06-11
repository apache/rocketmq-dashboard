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
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.model.ConsumerGroupRollBackStat;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.QueueStatInfo;
import org.apache.rocketmq.dashboard.model.StackResult;
import org.apache.rocketmq.dashboard.model.TopicConsumerInfo;
import org.apache.rocketmq.dashboard.service.client.ProxyAdmin;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.common.utils.ThreadUtils;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.model.request.DeleteSubGroupRequest;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.dashboard.service.AbstractCommonService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class ConsumerServiceImpl extends AbstractCommonService implements ConsumerService, InitializingBean, DisposableBean {
    private Logger logger = LoggerFactory.getLogger(ConsumerServiceImpl.class);

    @Resource
    protected ProxyAdmin proxyAdmin;
    @Resource
    private RMQConfigure configure;

    private static final Set<String> SYSTEM_GROUP_SET = new HashSet<>();

    private ExecutorService executorService;

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
        HashMap<String, List<String>> consumerGroupMap = Maps.newHashMap();
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
                SubscriptionGroupWrapper subscriptionGroupWrapper = mqAdminExt.getAllSubscriptionGroup(brokerData.selectBrokerAddr(), 3000L);
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
        List<GroupConsumeInfo> groupConsumeInfoList = Collections.synchronizedList(Lists.newArrayList());
        CountDownLatch countDownLatch = new CountDownLatch(consumerGroupMap.size());
        for (Map.Entry<String, List<String>> entry : consumerGroupMap.entrySet()) {
            String consumerGroup = entry.getKey();
            executorService.submit(() -> {
                try {
                    GroupConsumeInfo consumeInfo = queryGroup(consumerGroup, address);
                    consumeInfo.setAddress(entry.getValue());
                    groupConsumeInfoList.add(consumeInfo);
                } catch (Exception e) {
                    logger.error("queryGroup exception, consumerGroup: {}", consumerGroup, e);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("query consumerGroup countDownLatch await Exception", e);
        }

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

    @Override
    public GroupConsumeInfo queryGroup(String consumerGroup, String address) {
        GroupConsumeInfo groupConsumeInfo = new GroupConsumeInfo();
        try {
            ConsumeStats consumeStats = null;
            try {
                consumeStats = mqAdminExt.examineConsumeStats(consumerGroup);
            }
            catch (Exception e) {
                logger.warn("examineConsumeStats exception to consumerGroup {}, response [{}]", consumerGroup, e.getMessage());
            }

            ConsumerConnection consumerConnection = null;
            boolean isFifoType = examineSubscriptionGroupConfig(consumerGroup)
                    .stream().map(ConsumerConfigInfo::getSubscriptionGroupConfig)
                    .allMatch(SubscriptionGroupConfig::isConsumeMessageOrderly);

            try {
                if (StringUtils.isNotEmpty(address)) {
                    consumerConnection = proxyAdmin.examineConsumerConnectionInfo(address, consumerGroup);
                } else {
                    consumerConnection = mqAdminExt.examineConsumerConnectionInfo(consumerGroup);
                }
            } catch (Exception e) {
                logger.warn("examineConsumeStats exception to consumerGroup {}, response [{}]", consumerGroup, e.getMessage());
            }

            groupConsumeInfo.setGroup(consumerGroup);
            if (SYSTEM_GROUP_SET.contains(consumerGroup)) {
                groupConsumeInfo.setSubGroupType("SYSTEM");
            } else if (isFifoType) {
                groupConsumeInfo.setSubGroupType("FIFO");
            } else {
                groupConsumeInfo.setSubGroupType("NORMAL");
            }

            if (consumeStats != null) {
                groupConsumeInfo.setConsumeTps((int)consumeStats.getConsumeTps());
                groupConsumeInfo.setDiffTotal(consumeStats.computeTotalDiff());
            }

            if (consumerConnection != null) {
                groupConsumeInfo.setCount(consumerConnection.getConnectionSet().size());
                groupConsumeInfo.setMessageModel(consumerConnection.getMessageModel());
                groupConsumeInfo.setConsumeType(consumerConnection.getConsumeType());
                groupConsumeInfo.setVersion(MQVersion.getVersionDesc(consumerConnection.computeMinVersion()));
            }
        }
        catch (Exception e) {
            logger.warn("examineConsumeStats or examineConsumerConnectionInfo exception, "
                + consumerGroup, e);
        }
        return groupConsumeInfo;
    }

    @Override
    public List<TopicConsumerInfo> queryConsumeStatsListByGroupName(String groupName, String address) {
        ConsumeStats consumeStats;
        String topic = null;
        try {
            String[] addresses = address.split(",");
            String addr = addresses[0];
            consumeStats = mqAdminExt.examineConsumeStats(addr, groupName, null, 3000);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return toTopicConsumerInfoList(topic, consumeStats, groupName);
    }

    @Override
    public List<TopicConsumerInfo> queryConsumeStatsList(final String topic, String groupName) {
        ConsumeStats consumeStats = null;
        try {
            consumeStats = mqAdminExt.examineConsumeStats(groupName, topic);
        }
        catch (Exception e) {
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
        }
        catch (Exception err) {
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
                }
                catch (Exception ignore) {
                }
                group2ConsumerInfoMap.put(group, CollectionUtils.isEmpty(topicConsumerInfoList) ? new TopicConsumerInfo(topic) : topicConsumerInfoList.get(0));
            }
            return group2ConsumerInfoMap;
        }
        catch (Exception e) {
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
            }
            catch (MQClientException e) {
                if (ResponseCode.CONSUMER_NOT_ONLINE == e.getResponseCode()) {
                    try {
                        ConsumerGroupRollBackStat consumerGroupRollBackStat = new ConsumerGroupRollBackStat(true);
                        List<RollbackStats> rollbackStatsList = mqAdminExt.resetOffsetByTimestampOld(consumerGroup, resetOffsetRequest.getTopic(), resetOffsetRequest.getResetTime(), true);
                        consumerGroupRollBackStat.setRollbackStatsList(rollbackStatsList);
                        groupRollbackStats.put(consumerGroup, consumerGroupRollBackStat);
                        continue;
                    }
                    catch (Exception err) {
                        logger.error("op=resetOffset_which_not_online_error", err);
                    }
                }
                else {
                    logger.error("op=resetOffset_error", e);
                }
                groupRollbackStats.put(consumerGroup, new ConsumerGroupRollBackStat(false, e.getMessage()));
            }
            catch (Exception e) {
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
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : clusterInfo.getBrokerAddrTable().keySet()) { //foreach brokerName
                String brokerAddress = clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr();
                SubscriptionGroupConfig subscriptionGroupConfig = mqAdminExt.examineSubscriptionGroupConfig(brokerAddress, group);
                if (subscriptionGroupConfig == null) {
                    continue;
                }
                consumerConfigInfoList.add(new ConsumerConfigInfo(Lists.newArrayList(brokerName), subscriptionGroupConfig));
            }
        }
        catch (Exception e) {
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
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : deleteSubGroupRequest.getBrokerNameList()) {
                logger.info("addr={} groupName={}", clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), deleteSubGroupRequest.getGroupName());
                mqAdminExt.deleteSubscriptionGroup(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), deleteSubGroupRequest.getGroupName(), true);
                // Delete %RETRY%+Group and %DLQ%+Group in broker and namesrv
                deleteResources(MixAll.RETRY_GROUP_TOPIC_PREFIX + deleteSubGroupRequest.getGroupName(), brokerName, clusterInfo, deleteInNsFlag);
                deleteResources(MixAll.DLQ_GROUP_TOPIC_PREFIX + deleteSubGroupRequest.getGroupName(), brokerName, clusterInfo, deleteInNsFlag);
            }
        }
        catch (Exception e) {
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
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : changeToBrokerNameSet(clusterInfo.getClusterAddrTable(),
                consumerConfigInfo.getClusterNameList(), consumerConfigInfo.getBrokerNameList())) {
                mqAdminExt.createAndUpdateSubscriptionGroupConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), consumerConfigInfo.getSubscriptionGroupConfig());
            }
        }
        catch (Exception err) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }
    @Override
    public StackResult getConsumerStack(String consumerGroup, String clientId, boolean jstack) {
        ConsumerRunningInfo consumerRunningInfo = getConsumerRunningInfo(consumerGroup, clientId, jstack);
        Map<String, String> stackMap = new HashMap<>();
        Map<String, List<String>> map = formatThreadStack(consumerRunningInfo.getJstack());
        if (MapUtils.isNotEmpty(map)) {
            Set<String> threads = map.keySet();
            for (String thread : threads) {
                StringBuilder result = new StringBuilder();
                map.get(thread).forEach(s -> result.append(s).append("\n"));
                stackMap.put(thread, result.toString());
            }
        }
        return new StackResult(stackMap);
    }

    private Map<String, List<String>> formatThreadStack(String stack) {
        Map<String, List<String>> threadStackMap = new HashMap<>();
        List<String> stackList = Splitter.on("\n\n").splitToList(stack);
        for (String threadStack : stackList) {
            List<String> stacks = Splitter.on("\n").splitToList(threadStack);
            if (CollectionUtils.isNotEmpty(stacks)) {
                List<String> elements = new ArrayList<>();
                String threadName = null;
                for (String s : stacks) {
                    List<String> stackItem = Splitter.on("  ")
                            .omitEmptyStrings()
                            .trimResults()
                            .splitToList(s);
                    if (stackItem.size() == 1) {
                        String stackStr = stackItem.get(0);
                        if (threadName == null) {
                            int index = stackStr.indexOf("TID");
                            if (index != -1) {
                                threadName = stackStr.substring(0, index);
                            }
                        } else {
                            elements.add(stackStr.substring(threadName.length(), stackStr.length()));
                        }
                    }
                    if (stackItem.size() == 2) {
                        if (threadName == null) {
                            threadName = stackItem.get(0);
                        }
                        elements.add(stackItem.get(stackItem.size() - 1));
                    }
                }
                if (threadName != null) {
                    threadStackMap.put(threadName, elements);
                }
            }
        }
        return threadStackMap;
    }

}
