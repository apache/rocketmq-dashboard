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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.trace.TraceContext;
import org.apache.rocketmq.client.trace.TraceDispatcher;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.request.SendTopicMessageRequest;
import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;
import org.apache.rocketmq.dashboard.model.request.TopicTypeList;
import org.apache.rocketmq.dashboard.model.request.TopicTypeMeta;
import org.apache.rocketmq.dashboard.service.AbstractCommonService;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.command.CommandUtil;
import org.joor.Reflect;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.apache.rocketmq.common.TopicAttributes.TOPIC_MESSAGE_TYPE_ATTRIBUTE;

@Service
public class TopicServiceImpl extends AbstractCommonService implements TopicService {

    @Autowired
    private RMQConfigure configure;

    @Override
    public TopicList fetchAllTopicList(boolean skipSysProcess, boolean skipRetryAndDlq) {
        try {
            TopicList allTopics = mqAdminExt.fetchAllTopicList();
            TopicList sysTopics = getSystemTopicList();
            Set<String> topics =
                    allTopics.getTopicList().stream().map(topic -> {
                        if (!skipSysProcess && sysTopics.getTopicList().contains(topic)) {
                            topic = String.format("%s%s", "%SYS%", topic);
                        }
                        return topic;
                    }).filter(topic -> {
                        if (skipRetryAndDlq) {
                            return !(topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)
                                    || topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX));
                        }
                        return true;
                    }).collect(Collectors.toSet());
            allTopics.getTopicList().clear();
            allTopics.getTopicList().addAll(topics);
            return allTopics;
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public TopicTypeList examineAllTopicType() {
        ArrayList<TopicTypeMeta> topicTypes = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> messageTypes = new ArrayList<>();
        TopicList topicList = fetchAllTopicList(false, false);
        checkTopicType(topicList, topicTypes);
        topicTypes.sort((t1, t2) -> t1.getTopicName().compareTo(t2.getTopicName()));
        for (TopicTypeMeta topicTypeMeta : topicTypes) {
            names.add(topicTypeMeta.getTopicName());
            messageTypes.add(topicTypeMeta.getMessageType());
        }
        return new TopicTypeList(names, messageTypes);
    }

    private void checkTopicType(TopicList topicList, ArrayList<TopicTypeMeta> topicTypes) {
        for (String topicName : topicList.getTopicList()) {
            TopicTypeMeta topicType = new TopicTypeMeta();
            topicType.setTopicName(topicName);
            if (topicName.startsWith("%R")) {
                topicType.setMessageType("RETRY");
            } else if (topicName.startsWith("%D")) {
                topicType.setMessageType("DELAY");
            } else if (topicName.startsWith("%S")) {
                topicType.setMessageType("SYSTEM");
            } else {
                List<TopicConfigInfo> topicConfigInfos = examineTopicConfig(topicName);
                if (!CollectionUtils.isEmpty(topicConfigInfos)) {
                    topicType.setMessageType(topicConfigInfos.get(0).getMessageType());
                }
            }
            topicTypes.add(topicType);
        }
    }

    @Override
    public TopicStatsTable stats(String topic) {
        try {
            return mqAdminExt.examineTopicStats(topic);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public TopicRouteData route(String topic) {
        try {
            return mqAdminExt.examineTopicRouteInfo(topic);
        } catch (Exception ex) {
            Throwables.throwIfUnchecked(ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public GroupList queryTopicConsumerInfo(String topic) {
        try {
            return mqAdminExt.queryTopicConsumeByWho(topic);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createOrUpdate(TopicConfigInfo topicCreateOrUpdateRequest) {
        TopicConfig topicConfig = new TopicConfig();
        BeanUtils.copyProperties(topicCreateOrUpdateRequest, topicConfig);
        String messageType = topicCreateOrUpdateRequest.getMessageType();
        if (StringUtils.isBlank(messageType)) {
            messageType = TopicMessageType.NORMAL.name();
        }
        topicConfig.setAttributes(ImmutableMap.of("+".concat(TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName()), messageType));
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : changeToBrokerNameSet(clusterInfo.getClusterAddrTable(),
                    topicCreateOrUpdateRequest.getClusterNameList(), topicCreateOrUpdateRequest.getBrokerNameList())) {
                mqAdminExt.createAndUpdateTopicConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), topicConfig);
            }
        } catch (Exception err) {
            Throwables.throwIfUnchecked(err);
            throw new RuntimeException(err);
        }
    }

    @Override
    public TopicConfig examineTopicConfig(String topic, String brokerName) {
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            return mqAdminExt.examineTopicConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), topic);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TopicConfigInfo> examineTopicConfig(String topic) {
        List<TopicConfigInfo> topicConfigInfoList = Lists.newArrayList();
        TopicRouteData topicRouteData = route(topic);
        for (BrokerData brokerData : topicRouteData.getBrokerDatas()) {
            TopicConfigInfo topicConfigInfo = new TopicConfigInfo();
            TopicConfig topicConfig = examineTopicConfig(topic, brokerData.getBrokerName());
            BeanUtils.copyProperties(topicConfig, topicConfigInfo);
            topicConfigInfo.setBrokerNameList(Lists.newArrayList(brokerData.getBrokerName()));
            String messageType = topicConfig.getAttributes().get(TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName());
            if (StringUtils.isBlank(messageType)) {
                messageType = TopicMessageType.UNSPECIFIED.name();
            }
            topicConfigInfo.setMessageType(messageType);
            topicConfigInfoList.add(topicConfigInfo);
        }
        return topicConfigInfoList;
    }

    @Override
    public boolean deleteTopic(String topic, String clusterName) {
        try {
            if (StringUtils.isBlank(clusterName)) {
                return deleteTopic(topic);
            }
            Set<String> masterSet = CommandUtil.fetchMasterAddrByClusterName(mqAdminExt, clusterName);
            mqAdminExt.deleteTopicInBroker(masterSet, topic);
            Set<String> nameServerSet = null;
            if (StringUtils.isNotBlank(configure.getNamesrvAddr())) {
                String[] ns = configure.getNamesrvAddr().split(";");
                nameServerSet = new HashSet<String>(Arrays.asList(ns));
            }
            mqAdminExt.deleteTopicInNameServer(nameServerSet, topic);
        } catch (Exception err) {
            Throwables.throwIfUnchecked(err);
            throw new RuntimeException(err);
        }
        return true;
    }

    @Override
    public boolean deleteTopic(String topic) {
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        } catch (Exception err) {
            Throwables.throwIfUnchecked(err);
            throw new RuntimeException(err);
        }
        for (String clusterName : clusterInfo.getClusterAddrTable().keySet()) {
            deleteTopic(topic, clusterName);
        }
        return true;
    }

    @Override
    public boolean deleteTopicInBroker(String brokerName, String topic) {

        try {
            ClusterInfo clusterInfo = null;
            try {
                clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
            mqAdminExt.deleteTopicInBroker(Sets.newHashSet(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr()), topic);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        return true;
    }

    public DefaultMQProducer buildDefaultMQProducer(String producerGroup, RPCHook rpcHook) {
        return buildDefaultMQProducer(producerGroup, rpcHook, false);
    }

    public DefaultMQProducer buildDefaultMQProducer(String producerGroup, RPCHook rpcHook, boolean traceEnabled) {
        DefaultMQProducer defaultMQProducer = new DefaultMQProducer(producerGroup, rpcHook, traceEnabled, TopicValidator.RMQ_SYS_TRACE_TOPIC);
        defaultMQProducer.setUseTLS(configure.isUseTLS());
        return defaultMQProducer;
    }

    public TransactionMQProducer buildTransactionMQProducer(String producerGroup, RPCHook rpcHook, boolean traceEnabled) {
        TransactionMQProducer defaultMQProducer = new TransactionMQProducer(null, producerGroup, rpcHook, traceEnabled, TopicValidator.RMQ_SYS_TRACE_TOPIC);
        defaultMQProducer.setUseTLS(configure.isUseTLS());
        return defaultMQProducer;
    }

    private TopicList getSystemTopicList() {
        RPCHook rpcHook = null;
        boolean isEnableAcl = !StringUtils.isEmpty(configure.getAccessKey()) && !StringUtils.isEmpty(configure.getSecretKey());
        if (isEnableAcl) {
            rpcHook = new AclClientRPCHook(new SessionCredentials(configure.getAccessKey(), configure.getSecretKey()));
        }
        DefaultMQProducer producer = buildDefaultMQProducer(MixAll.SELF_TEST_PRODUCER_GROUP, rpcHook);
        producer.setInstanceName(String.valueOf(System.currentTimeMillis()));
        producer.setNamesrvAddr(configure.getNamesrvAddr());

        try {
            producer.start();
            return producer.getDefaultMQProducerImpl().getmQClientFactory().getMQClientAPIImpl().getSystemTopicList(20000L);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        } finally {
            producer.shutdown();
        }
    }

    @Override
    public SendResult sendTopicMessageRequest(SendTopicMessageRequest sendTopicMessageRequest) {
        List<TopicConfigInfo> topicConfigInfos = examineTopicConfig(sendTopicMessageRequest.getTopic());
        String messageType = topicConfigInfos.get(0).getMessageType();
        AclClientRPCHook rpcHook = null;
        if (configure.isACLEnabled()) {
            rpcHook = new AclClientRPCHook(new SessionCredentials(
                    configure.getAccessKey(),
                    configure.getSecretKey()
            ));
        }
        if (TopicMessageType.TRANSACTION.getValue().equals(messageType)) {
            // transaction message
            TransactionListener transactionListener = new TransactionListenerImpl();

            TransactionMQProducer producer = buildTransactionMQProducer(MixAll.SELF_TEST_PRODUCER_GROUP, rpcHook, sendTopicMessageRequest.isTraceEnabled());
            producer.setInstanceName(String.valueOf(System.currentTimeMillis()));
            producer.setNamesrvAddr(configure.getNamesrvAddr());
            producer.setTransactionListener(transactionListener);
            try {
                producer.start();
                Message msg = new Message(sendTopicMessageRequest.getTopic(),
                        sendTopicMessageRequest.getTag(),
                        sendTopicMessageRequest.getKey(),
                        sendTopicMessageRequest.getMessageBody().getBytes()
                );
                return producer.sendMessageInTransaction(msg, null);
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            } finally {
                waitSendTraceFinish(producer, sendTopicMessageRequest.isTraceEnabled());
                producer.shutdown();
            }
        } else {
            // no transaction message
            DefaultMQProducer producer = null;
            producer = buildDefaultMQProducer(MixAll.SELF_TEST_PRODUCER_GROUP, rpcHook, sendTopicMessageRequest.isTraceEnabled());
            producer.setInstanceName(String.valueOf(System.currentTimeMillis()));
            producer.setNamesrvAddr(configure.getNamesrvAddr());
            try {
                producer.start();
                Message msg = new Message(sendTopicMessageRequest.getTopic(),
                        sendTopicMessageRequest.getTag(),
                        sendTopicMessageRequest.getKey(),
                        sendTopicMessageRequest.getMessageBody().getBytes()
                );
                return producer.send(msg);
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            } finally {
                waitSendTraceFinish(producer, sendTopicMessageRequest.isTraceEnabled());
                producer.shutdown();
            }
        }

    }

    private void waitSendTraceFinish(DefaultMQProducer producer, boolean traceEnabled) {
        if (!traceEnabled) {
            return;
        }
        try {
            TraceDispatcher traceDispatcher = Reflect.on(producer).field("traceDispatcher").get();
            if (traceDispatcher != null) {
                ArrayBlockingQueue<TraceContext> traceContextQueue = Reflect.on(traceDispatcher).field("traceContextQueue").get();
                while (traceContextQueue.size() > 0) {
                    Thread.sleep(1);
                }
            }
            // wait another 150ms until async request send finish
            // after new RocketMQ version released, this logic can be removed
            // https://github.com/apache/rocketmq/pull/2989
            Thread.sleep(150);
        } catch (Exception ignore) {
        }
    }

    static class TransactionListenerImpl implements TransactionListener {
        private AtomicInteger transactionIndex = new AtomicInteger(0);

        private ConcurrentHashMap<String, Integer> localTrans = new ConcurrentHashMap<>();

        @Override
        public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            return LocalTransactionState.COMMIT_MESSAGE;
        }

        @Override
        public LocalTransactionState checkLocalTransaction(MessageExt msg) {
            return LocalTransactionState.COMMIT_MESSAGE;
        }
    }
}
