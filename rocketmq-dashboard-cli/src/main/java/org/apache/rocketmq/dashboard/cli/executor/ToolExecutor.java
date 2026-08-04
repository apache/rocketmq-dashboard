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
package org.apache.rocketmq.dashboard.cli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes registered tools ({@code rmq.resource.verb}) against a live
 * RocketMQ cluster via {@link MQAdminExt}. Shared by the CLI and the MCP
 * server ({@code tools/call}), so it never writes to stdout and always
 * returns plain {@code Map}/{@code List} structures that are safe to
 * serialize with Jackson.
 *
 * <p>Cluster resolution: the {@code cluster} argument is treated as a
 * NameServer address when it contains a colon (e.g. {@code 127.0.0.1:9876}),
 * otherwise it is resolved as a cluster name from the rmqctl CLI
 * configuration (falling back to the current context when absent).</p>
 */
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Execute a tool with the given arguments against the live cluster.
     *
     * @param tool      tool definition from the shared ToolRegistry
     * @param arguments arguments map (from CLI options or MCP tools/call)
     * @return plain Map/List result, ready for JSON serialization
     * @throws UnsupportedOperationException if the tool has no live implementation
     * @throws Exception                     on connection or execution failure
     */
    public Object execute(ToolDefinition tool, Map<String, Object> arguments) throws Exception {
        Map<String, Object> args = arguments != null ? arguments : new LinkedHashMap<>();
        // Static enumerations need no live cluster connection.
        if ("rmq.topic.types".equals(tool.getName())) {
            return listTopicTypes();
        }
        // Namespace operations are not exposed by the live cluster admin API; reject early.
        if ("namespace".equals(tool.getResource())) {
            throw new UnsupportedOperationException(
                    "Namespace operations are not supported by the connected cluster admin API.");
        }
        // Fail fast on missing required arguments before paying the connection cost.
        validateRequiredArguments(tool, args);
        try (AdminClientHelper admin = connect(getString(args, "cluster"))) {
            switch (tool.getName()) {
                case "rmq.cluster.list":
                case "rmq.cluster.describe":
                    return describeCluster(admin);
                case "rmq.topic.list":
                    return listTopics(admin);
                case "rmq.topic.describe":
                    return describeTopic(admin, requireString(args, "topic"));
                case "rmq.topic.create":
                case "rmq.topic.update":
                    return upsertTopic(admin, args);
                case "rmq.topic.delete":
                    return deleteTopic(admin, requireString(args, "topic"));
                case "rmq.route.list":
                    return routeList(admin, requireString(args, "topic"));
                case "rmq.route.describe":
                    return routeDescribe(admin, requireString(args, "topic"));
                case "rmq.group.list":
                    return listGroups(admin);
                case "rmq.group.describe":
                    return describeGroup(admin, requireString(args, "group"));
                case "rmq.group.create":
                case "rmq.group.update":
                    return upsertGroup(admin, args);
                case "rmq.group.reset-offset":
                    return resetOffset(admin, args);
                case "rmq.group.delete":
                    return deleteGroup(admin, requireString(args, "group"));
                case "rmq.group.progress":
                    return groupProgress(admin, requireString(args, "group"));
                case "rmq.message.query-by-id":
                    return queryMessageById(admin, args);
                case "rmq.message.query-by-time":
                    return queryMessageByTime(admin, args);
                case "rmq.message.resend":
                    return resendMessage(admin, args);
                case "rmq.dlq.list":
                    return dlqList(admin, args);
                case "rmq.dlq.resend":
                    return dlqResend(admin, args);
                case "rmq.client.list":
                    return listClients(admin, requireString(args, "group"));
                case "rmq.client.describe":
                    return describeClient(admin, args);
                case "rmq.acl.list":
                    return listAcl(admin);
                case "rmq.acl.create":
                    return createAcl(admin, args);
                case "rmq.acl.update":
                    return updateAcl(admin, args);
                case "rmq.acl.delete":
                    return deleteAcl(admin, args);
                case "rmq.broker.list":
                    return listBrokers(admin);
                case "rmq.broker.describe":
                    return describeBroker(admin, requireString(args, "brokerName"));
                case "rmq.broker.config":
                    return brokerConfig(admin, args);
                case "rmq.metrics.query":
                    return queryMetrics(admin, args);
                case "rmq.capabilities.detect":
                    return detectCapabilities(admin);
                default:
                    throw new UnsupportedOperationException("No live implementation for tool: " + tool.getName());
            }
        }
    }

    // ---- connection -----------------------------------------------------------

    private AdminClientHelper connect(String cluster) throws Exception {
        if (cluster != null && cluster.contains(":")) {
            return AdminClientHelper.connectDirect(cluster);
        }
        return AdminClientHelper.connectQuiet(cluster);
    }

    /**
     * Validates declared required parameters before any connection is opened,
     * so pure argument mistakes never pay the cluster connection cost.
     * The {@code cluster} argument is exempt: it may be omitted and resolved
     * from the current CLI context inside {@link #connect(String)}.
     */
    private static void validateRequiredArguments(ToolDefinition tool, Map<String, Object> args) {
        if (tool.getParams() == null) {
            return;
        }
        for (ParamSchema param : tool.getParams()) {
            if (!param.isRequired() || "cluster".equals(param.getName())) {
                continue;
            }
            String value = getString(args, param.getName());
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Missing required argument: " + param.getName());
            }
        }
    }

    // ---- cluster ----------------------------------------------------------------

    private Map<String, Object> describeCluster(AdminClientHelper admin) throws Exception {
        ClusterInfo clusterInfo = admin.getClusterInfo();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> clusters = new ArrayList<>();
        if (clusterInfo.getClusterAddrTable() != null) {
            clusterInfo.getClusterAddrTable().forEach((clusterName, brokerNames) -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("clusterName", clusterName);
                entry.put("brokerNames", new ArrayList<>(brokerNames));
                clusters.add(entry);
            });
        }
        result.put("clusters", clusters);
        result.put("brokers", brokerTableToList(clusterInfo));
        result.put("namesrvAddr", admin.getNamesrvAddr());
        return result;
    }

    // ---- topic ------------------------------------------------------------------

    private List<String> listTopics(AdminClientHelper admin) throws Exception {
        TopicList topicList = admin.getMqAdminExt().fetchAllTopicList();
        return new ArrayList<>(new TreeSet<>(topicList.getTopicList()));
    }

    private Map<String, Object> describeTopic(AdminClientHelper admin, String topic) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);

        TopicConfig config = admin.examineTopicConfig(topic);
        if (config != null) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("readQueueNums", config.getReadQueueNums());
            cfg.put("writeQueueNums", config.getWriteQueueNums());
            cfg.put("perm", config.getPerm());
            cfg.put("order", config.isOrder());
            cfg.put("topicMessageType", config.getTopicMessageType() != null
                    ? config.getTopicMessageType().name() : null);
            result.put("config", cfg);
        }

        TopicRouteData route = admin.getMqAdminExt().examineTopicRouteInfo(topic);
        List<Map<String, Object>> queues = new ArrayList<>();
        if (route.getQueueDatas() != null) {
            for (QueueData qd : route.getQueueDatas()) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("brokerName", qd.getBrokerName());
                q.put("readQueueNums", qd.getReadQueueNums());
                q.put("writeQueueNums", qd.getWriteQueueNums());
                q.put("perm", qd.getPerm());
                queues.add(q);
            }
        }
        result.put("queueDatas", queues);
        List<Map<String, Object>> brokers = new ArrayList<>();
        if (route.getBrokerDatas() != null) {
            for (BrokerData bd : route.getBrokerDatas()) {
                brokers.add(brokerDataToMap(bd));
            }
        }
        result.put("brokerDatas", brokers);
        return result;
    }

    private Map<String, Object> upsertTopic(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String topic = requireString(args, "topic");
        TopicConfig config = admin.examineTopicConfig(topic);
        if (config == null) {
            config = new TopicConfig(topic);
        }
        Integer readQueueNums = getInt(args, "readQueueNums");
        if (readQueueNums != null) {
            config.setReadQueueNums(readQueueNums);
        }
        Integer writeQueueNums = getInt(args, "writeQueueNums");
        if (writeQueueNums != null) {
            config.setWriteQueueNums(writeQueueNums);
        }
        Integer perm = getInt(args, "perm");
        if (perm != null) {
            config.setPerm(perm);
        }
        int brokerCount = admin.createTopicOnAllBrokers(config);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        result.put("readQueueNums", config.getReadQueueNums());
        result.put("writeQueueNums", config.getWriteQueueNums());
        result.put("perm", config.getPerm());
        result.put("appliedBrokers", brokerCount);
        return result;
    }

    private Map<String, Object> deleteTopic(AdminClientHelper admin, String topic) throws Exception {
        admin.deleteTopicFromCluster(topic);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        result.put("deleted", true);
        return result;
    }

    // ---- route ------------------------------------------------------------------

    private List<Map<String, Object>> routeList(AdminClientHelper admin, String topic) throws Exception {
        TopicRouteData route = admin.getMqAdminExt().examineTopicRouteInfo(topic);
        List<Map<String, Object>> queues = new ArrayList<>();
        if (route.getQueueDatas() != null) {
            for (QueueData qd : route.getQueueDatas()) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("brokerName", qd.getBrokerName());
                q.put("readQueueNums", qd.getReadQueueNums());
                q.put("writeQueueNums", qd.getWriteQueueNums());
                q.put("perm", qd.getPerm());
                queues.add(q);
            }
        }
        return queues;
    }

    private Map<String, Object> routeDescribe(AdminClientHelper admin, String topic) throws Exception {
        TopicRouteData route = admin.getMqAdminExt().examineTopicRouteInfo(topic);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        List<Map<String, Object>> queues = new ArrayList<>();
        if (route.getQueueDatas() != null) {
            for (QueueData qd : route.getQueueDatas()) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("brokerName", qd.getBrokerName());
                q.put("readQueueNums", qd.getReadQueueNums());
                q.put("writeQueueNums", qd.getWriteQueueNums());
                q.put("perm", qd.getPerm());
                queues.add(q);
            }
        }
        result.put("queueDatas", queues);
        List<Map<String, Object>> brokers = new ArrayList<>();
        if (route.getBrokerDatas() != null) {
            for (BrokerData bd : route.getBrokerDatas()) {
                brokers.add(brokerDataToMap(bd));
            }
        }
        result.put("brokerDatas", brokers);
        return result;
    }

    // ---- group ------------------------------------------------------------------

    private List<Map<String, Object>> listGroups(AdminClientHelper admin) throws Exception {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (String addr : admin.getMasterBrokerAddresses()) {
            try {
                SubscriptionGroupWrapper wrapper = admin.getMqAdminExt().getAllSubscriptionGroup(addr, 10000);
                if (wrapper == null || wrapper.getSubscriptionGroupTable() == null) {
                    continue;
                }
                for (SubscriptionGroupConfig cfg : wrapper.getSubscriptionGroupTable().values()) {
                    merged.putIfAbsent(cfg.getGroupName(), groupConfigToMap(cfg));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch subscription groups from broker {}: {}", addr, e.getMessage());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Map<String, Object> describeGroup(AdminClientHelper admin, String group) throws Exception {
        SubscriptionGroupConfig cfg = admin.examineSubscriptionGroupConfig(group);
        if (cfg == null) {
            throw new IllegalArgumentException("Consumer group not found: " + group);
        }
        Map<String, Object> result = groupConfigToMap(cfg);
        try {
            ConsumerConnection conn = admin.getMqAdminExt().examineConsumerConnectionInfo(group);
            result.put("consumeType", conn.getConsumeType() != null ? conn.getConsumeType().name() : null);
            result.put("messageModel", conn.getMessageModel() != null ? conn.getMessageModel().name() : null);
            result.put("connectionCount", conn.getConnectionSet() != null ? conn.getConnectionSet().size() : 0);
        } catch (Exception e) {
            log.debug("No online consumers for group {}: {}", group, e.getMessage());
            result.put("connectionCount", 0);
        }
        return result;
    }

    private Map<String, Object> upsertGroup(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String group = requireString(args, "group");
        SubscriptionGroupConfig config = admin.examineSubscriptionGroupConfig(group);
        if (config == null) {
            config = new SubscriptionGroupConfig();
            config.setGroupName(group);
        }
        String consumeMode = getString(args, "consumeMode");
        if (consumeMode != null) {
            config.setConsumeBroadcastEnable("BROADCAST".equalsIgnoreCase(consumeMode));
        }
        Integer retryMaxTimes = getInt(args, "retryMaxTimes");
        if (retryMaxTimes != null) {
            config.setRetryMaxTimes(retryMaxTimes);
        }
        int brokerCount = admin.createConsumerGroupOnAllBrokers(config);
        Map<String, Object> result = groupConfigToMap(config);
        result.put("appliedBrokers", brokerCount);
        return result;
    }

    private Map<String, Object> resetOffset(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String group = requireString(args, "group");
        String topic = requireString(args, "topic");
        long timestamp = requireLong(args, "timestamp");
        Map<MessageQueue, Long> offsets = admin.getMqAdminExt()
                .resetOffsetByTimestamp(topic, group, timestamp, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", group);
        result.put("topic", topic);
        result.put("timestamp", timestamp);
        List<Map<String, Object>> queues = new ArrayList<>();
        if (offsets != null) {
            offsets.forEach((mq, offset) -> {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("brokerName", mq.getBrokerName());
                q.put("queueId", mq.getQueueId());
                q.put("offset", offset);
                queues.add(q);
            });
        }
        result.put("resetQueues", queues);
        return result;
    }

    private Map<String, Object> deleteGroup(AdminClientHelper admin, String group) throws Exception {
        int brokerCount = admin.deleteConsumerGroupFromAllBrokers(group);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", group);
        result.put("deleted", true);
        result.put("appliedBrokers", brokerCount);
        return result;
    }

    private Map<String, Object> groupProgress(AdminClientHelper admin, String group) throws Exception {
        ConsumeStats stats = admin.getMqAdminExt().examineConsumeStats(group);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", group);
        result.put("consumeTps", stats.getConsumeTps());
        long totalDiff = 0;
        List<Map<String, Object>> queues = new ArrayList<>();
        if (stats.getOffsetTable() != null) {
            for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                long diff = entry.getValue().getBrokerOffset() - entry.getValue().getConsumerOffset();
                totalDiff += diff;
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("topic", entry.getKey().getTopic());
                q.put("brokerName", entry.getKey().getBrokerName());
                q.put("queueId", entry.getKey().getQueueId());
                q.put("brokerOffset", entry.getValue().getBrokerOffset());
                q.put("consumerOffset", entry.getValue().getConsumerOffset());
                q.put("diff", diff);
                queues.add(q);
            }
        }
        result.put("totalDiff", totalDiff);
        result.put("data", queues);
        return result;
    }

    // ---- message ------------------------------------------------------------------

    private Map<String, Object> queryMessageById(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String topic = requireString(args, "topic");
        String msgId = requireString(args, "msgId");
        MessageExt msg = admin.getMqAdminExt().viewMessage(topic, msgId);
        return messageToMap(msg);
    }

    private List<Map<String, Object>> queryMessageByTime(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String topic = requireString(args, "topic");
        long beginTime = requireLong(args, "beginTime");
        long endTime = requireLong(args, "endTime");
        Integer maxNum = getInt(args, "maxNum");
        QueryResult queryResult = admin.getMqAdminExt()
                .queryMessage(topic, "*", maxNum != null ? maxNum : 32, beginTime, endTime);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (queryResult != null && queryResult.getMessageList() != null) {
            for (MessageExt msg : queryResult.getMessageList()) {
                messages.add(messageToMap(msg));
            }
        }
        return messages;
    }

    private Map<String, Object> resendMessage(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String topic = requireString(args, "topic");
        String msgId = requireString(args, "msgId");
        String group = requireString(args, "group");

        ConsumerConnection conn = admin.getMqAdminExt().examineConsumerConnectionInfo(group);
        if (conn.getConnectionSet() == null || conn.getConnectionSet().isEmpty()) {
            throw new IllegalStateException("No online consumers in group " + group + " to resend the message to.");
        }
        String clientId = conn.getConnectionSet().iterator().next().getClientId();
        ConsumeMessageDirectlyResult directResult = admin.getMqAdminExt()
                .consumeMessageDirectly(group, clientId, topic, msgId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msgId", msgId);
        result.put("group", group);
        result.put("clientId", clientId);
        result.put("consumeResult", directResult.getConsumeResult() != null
                ? directResult.getConsumeResult().name() : null);
        result.put("spentTimeMills", directResult.getSpentTimeMills());
        result.put("remark", directResult.getRemark());
        return result;
    }

    // ---- dlq ----------------------------------------------------------------------

    private static String dlqTopicOf(String group) {
        return "%DLQ%" + group;
    }

    private List<Map<String, Object>> dlqList(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String group = requireString(args, "group");
        String dlqTopic = dlqTopicOf(group);
        long end = getLong(args, "endTime") != null ? requireLong(args, "endTime") : System.currentTimeMillis();
        long begin = getLong(args, "beginTime") != null ? requireLong(args, "beginTime") : end - 3_600_000L;
        Integer maxNum = getInt(args, "maxNum");
        QueryResult queryResult = admin.getMqAdminExt()
                .queryMessage(dlqTopic, "*", maxNum != null ? maxNum : 32, begin, end);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (queryResult != null && queryResult.getMessageList() != null) {
            for (MessageExt msg : queryResult.getMessageList()) {
                messages.add(messageToMap(msg));
            }
        }
        return messages;
    }

    private Map<String, Object> dlqResend(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String group = requireString(args, "group");
        String msgId = requireString(args, "msgId");
        String topic = requireString(args, "topic");
        String dlqTopic = dlqTopicOf(group);

        ConsumerConnection conn = admin.getMqAdminExt().examineConsumerConnectionInfo(group);
        if (conn.getConnectionSet() == null || conn.getConnectionSet().isEmpty()) {
            throw new IllegalStateException("No online consumers in group " + group + " to resend the dead-letter message to.");
        }
        String clientId = conn.getConnectionSet().iterator().next().getClientId();
        ConsumeMessageDirectlyResult directResult = admin.getMqAdminExt()
                .consumeMessageDirectly(group, clientId, dlqTopic, msgId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msgId", msgId);
        result.put("group", group);
        result.put("dlqTopic", dlqTopic);
        result.put("topic", topic);
        result.put("clientId", clientId);
        result.put("consumeResult", directResult.getConsumeResult() != null
                ? directResult.getConsumeResult().name() : null);
        result.put("spentTimeMills", directResult.getSpentTimeMills());
        result.put("remark", directResult.getRemark());
        return result;
    }

    // ---- client -------------------------------------------------------------------

    private List<Map<String, Object>> listClients(AdminClientHelper admin, String group) throws Exception {
        ConsumerConnection conn = admin.getMqAdminExt().examineConsumerConnectionInfo(group);
        List<Map<String, Object>> clients = new ArrayList<>();
        if (conn.getConnectionSet() != null) {
            for (Connection c : conn.getConnectionSet()) {
                clients.add(connectionToMap(c, group));
            }
        }
        return clients;
    }

    private Map<String, Object> describeClient(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String clientId = requireString(args, "clientId");
        String group = getString(args, "group");
        if (group == null) {
            throw new IllegalArgumentException("'group' argument is required to locate client " + clientId);
        }
        ConsumerConnection conn = admin.getMqAdminExt().examineConsumerConnectionInfo(group);
        if (conn.getConnectionSet() != null) {
            for (Connection c : conn.getConnectionSet()) {
                if (clientId.equals(c.getClientId())) {
                    Map<String, Object> result = connectionToMap(c, group);
                    result.put("consumeType", conn.getConsumeType() != null ? conn.getConsumeType().name() : null);
                    result.put("messageModel", conn.getMessageModel() != null ? conn.getMessageModel().name() : null);
                    return result;
                }
            }
        }
        throw new IllegalArgumentException("Client not found in group " + group + ": " + clientId);
    }

    // ---- acl ----------------------------------------------------------------------

    private List<Object> listAcl(AdminClientHelper admin) throws Exception {
        List<Object> policies = new ArrayList<>();
        for (String addr : admin.getMasterBrokerAddresses()) {
            try {
                List<?> acls = admin.getMqAdminExt().listAcl(addr, null, null);
                if (acls != null) {
                    for (Object acl : acls) {
                        policies.add(objectMapper.convertValue(acl, Map.class));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to list ACLs from broker {}: {}", addr, e.getMessage());
            }
        }
        return policies;
    }

    private Map<String, Object> createAcl(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String subject = toAclSubject(requireString(args, "username"));
        List<String> resources = List.of(requireString(args, "resource"));
        List<String> actions = splitActions(requireString(args, "actions"));
        String decision = getString(args, "decision") != null ? getString(args, "decision") : "ALLOW";
        for (String addr : admin.getMasterBrokerAddresses()) {
            admin.getMqAdminExt().createAcl(addr, subject, resources, actions, null, decision);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", subject);
        result.put("resources", resources);
        result.put("actions", actions);
        result.put("decision", decision);
        result.put("created", true);
        return result;
    }

    private Map<String, Object> updateAcl(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String subject = toAclSubject(requireString(args, "policyId"));
        String resource = getString(args, "resource");
        List<String> resources = resource != null ? List.of(resource) : List.of();
        List<String> actions = getString(args, "actions") != null
                ? splitActions(getString(args, "actions")) : List.of();
        String decision = getString(args, "decision") != null ? getString(args, "decision") : "ALLOW";
        for (String addr : admin.getMasterBrokerAddresses()) {
            admin.getMqAdminExt().updateAcl(addr, subject, resources, actions, null, decision);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", subject);
        result.put("resources", resources);
        result.put("actions", actions);
        result.put("decision", decision);
        result.put("updated", true);
        return result;
    }

    private Map<String, Object> deleteAcl(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String subject = toAclSubject(requireString(args, "policyId"));
        String resource = getString(args, "resource");
        for (String addr : admin.getMasterBrokerAddresses()) {
            admin.getMqAdminExt().deleteAcl(addr, subject, resource);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", subject);
        result.put("deleted", true);
        return result;
    }

    // ---- broker -------------------------------------------------------------------

    private List<Map<String, Object>> listBrokers(AdminClientHelper admin) throws Exception {
        return brokerTableToList(admin.getClusterInfo());
    }

    private Map<String, Object> describeBroker(AdminClientHelper admin, String brokerName) throws Exception {
        String brokerAddr = resolveBrokerAddr(admin, brokerName);
        KVTable runtimeStats = admin.getMqAdminExt().fetchBrokerRuntimeStats(brokerAddr);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brokerName", brokerName);
        result.put("brokerAddr", brokerAddr);
        result.put("runtimeStats", runtimeStats != null
                ? new LinkedHashMap<>(runtimeStats.getTable()) : new LinkedHashMap<>());
        return result;
    }

    private Map<String, Object> brokerConfig(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String brokerName = requireString(args, "brokerName");
        String brokerAddr = resolveBrokerAddr(admin, brokerName);
        String configKey = getString(args, "configKey");
        String configValue = getString(args, "configValue");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brokerName", brokerName);
        result.put("brokerAddr", brokerAddr);

        if (configKey != null && configValue != null) {
            Properties properties = new Properties();
            properties.setProperty(configKey, configValue);
            admin.getMqAdminExt().updateBrokerConfig(brokerAddr, properties);
            result.put("updated", Map.of(configKey, configValue));
        } else {
            Properties properties = admin.getMqAdminExt().getBrokerConfig(brokerAddr);
            Map<String, Object> config = new LinkedHashMap<>();
            if (configKey != null) {
                config.put(configKey, properties.getProperty(configKey));
            } else {
                properties.stringPropertyNames().stream().sorted()
                        .forEach(k -> config.put(k, properties.getProperty(k)));
            }
            result.put("config", config);
        }
        return result;
    }

    // ---- metrics ------------------------------------------------------------------

    private Map<String, Object> queryMetrics(AdminClientHelper admin, Map<String, Object> args) throws Exception {
        String metricType = requireString(args, "metricType");
        String targetName = getString(args, "targetName");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricType", metricType);
        result.put("targetName", targetName);

        switch (metricType.toLowerCase()) {
            case "topic": {
                if (targetName == null) {
                    throw new IllegalArgumentException("targetName (topic) is required for topic metrics.");
                }
                TopicStatsTable stats = admin.getMqAdminExt().examineTopicStats(targetName);
                List<Map<String, Object>> queues = new ArrayList<>();
                if (stats.getOffsetTable() != null) {
                    for (Map.Entry<MessageQueue, TopicOffset> entry : stats.getOffsetTable().entrySet()) {
                        Map<String, Object> q = new LinkedHashMap<>();
                        q.put("brokerName", entry.getKey().getBrokerName());
                        q.put("queueId", entry.getKey().getQueueId());
                        q.put("minOffset", entry.getValue().getMinOffset());
                        q.put("maxOffset", entry.getValue().getMaxOffset());
                        q.put("lastUpdateTimestamp", entry.getValue().getLastUpdateTimestamp());
                        queues.add(q);
                    }
                }
                result.put("data", queues);
                break;
            }
            case "consumer": {
                if (targetName == null) {
                    throw new IllegalArgumentException("targetName (consumer group) is required for consumer metrics.");
                }
                ConsumeStats stats = admin.getMqAdminExt().examineConsumeStats(targetName);
                result.put("consumeTps", stats.getConsumeTps());
                long totalDiff = 0;
                List<Map<String, Object>> queues = new ArrayList<>();
                if (stats.getOffsetTable() != null) {
                    for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                        long diff = entry.getValue().getBrokerOffset() - entry.getValue().getConsumerOffset();
                        totalDiff += diff;
                        Map<String, Object> q = new LinkedHashMap<>();
                        q.put("topic", entry.getKey().getTopic());
                        q.put("brokerName", entry.getKey().getBrokerName());
                        q.put("queueId", entry.getKey().getQueueId());
                        q.put("brokerOffset", entry.getValue().getBrokerOffset());
                        q.put("consumerOffset", entry.getValue().getConsumerOffset());
                        q.put("diff", diff);
                        queues.add(q);
                    }
                }
                result.put("totalDiff", totalDiff);
                result.put("data", queues);
                break;
            }
            case "broker": {
                if (targetName == null) {
                    throw new IllegalArgumentException("targetName (broker name) is required for broker metrics.");
                }
                String brokerAddr = resolveBrokerAddr(admin, targetName);
                KVTable runtimeStats = admin.getMqAdminExt().fetchBrokerRuntimeStats(brokerAddr);
                result.put("data", runtimeStats != null
                        ? new LinkedHashMap<>(runtimeStats.getTable()) : new LinkedHashMap<>());
                break;
            }
            case "cluster":
            case "system":
            default: {
                Map<String, Object> brokerStats = new LinkedHashMap<>();
                for (Map.Entry<String, BrokerData> entry
                        : admin.getClusterInfo().getBrokerAddrTable().entrySet()) {
                    String addr = entry.getValue().selectBrokerAddr();
                    if (addr == null) {
                        continue;
                    }
                    try {
                        KVTable runtimeStats = admin.getMqAdminExt().fetchBrokerRuntimeStats(addr);
                        Map<String, String> table = runtimeStats.getTable();
                        Map<String, Object> brief = new LinkedHashMap<>();
                        for (String key : new String[]{"brokerVersionDesc", "putTps", "getTransferredTps",
                            "msgPutTotalTodayNow", "msgGetTotalTodayNow", "runtime", "commitLogDiskRatio"}) {
                            if (table.containsKey(key)) {
                                brief.put(key, table.get(key));
                            }
                        }
                        brokerStats.put(entry.getKey(), brief);
                    } catch (Exception e) {
                        log.warn("Failed to fetch runtime stats from broker {}: {}", addr, e.getMessage());
                    }
                }
                result.put("data", brokerStats);
                break;
            }
        }
        return result;
    }

    // ---- capabilities ---------------------------------------------------------------

    private Map<String, Object> detectCapabilities(AdminClientHelper admin) throws Exception {
        ClusterInfo clusterInfo = admin.getClusterInfo();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namesrvAddr", admin.getNamesrvAddr());
        result.put("clusterNames", clusterInfo.getClusterAddrTable() != null
                ? new ArrayList<>(clusterInfo.getClusterAddrTable().keySet()) : List.of());
        result.put("brokerCount", clusterInfo.getBrokerAddrTable() != null
                ? clusterInfo.getBrokerAddrTable().size() : 0);
        result.put("brokers", brokerTableToList(clusterInfo));
        return result;
    }

    // ---- topic types (static enumeration) -------------------------------------------

    private List<Map<String, Object>> listTopicTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        types.add(typeEntry("NORMAL", "Standard topic. No ordering or transaction guarantees."));
        types.add(typeEntry("FIFO", "Ordered topic. Messages are consumed in publish order."));
        types.add(typeEntry("DELAY", "Delayed topic. Messages are delivered after a fixed delay."));
        types.add(typeEntry("TRANSACTION", "Transaction topic. Supports distributed transactions via half-messages."));
        types.add(typeEntry("LITE", "Lite topic. Lightweight topic without the full feature set."));
        return types;
    }

    private static Map<String, Object> typeEntry(String type, String description) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("description", description);
        return entry;
    }

    // ---- converters -----------------------------------------------------------------

    private List<Map<String, Object>> brokerTableToList(ClusterInfo clusterInfo) {
        List<Map<String, Object>> brokers = new ArrayList<>();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            for (BrokerData bd : clusterInfo.getBrokerAddrTable().values()) {
                brokers.add(brokerDataToMap(bd));
            }
        }
        return brokers;
    }

    private Map<String, Object> brokerDataToMap(BrokerData bd) {
        Map<String, Object> broker = new LinkedHashMap<>();
        broker.put("brokerName", bd.getBrokerName());
        broker.put("cluster", bd.getCluster());
        Map<String, String> addrs = new LinkedHashMap<>();
        if (bd.getBrokerAddrs() != null) {
            bd.getBrokerAddrs().forEach((id, addr) ->
                    addrs.put(id == 0L ? "master" : "slave-" + id, addr));
        }
        broker.put("addresses", addrs);
        return broker;
    }

    private Map<String, Object> groupConfigToMap(SubscriptionGroupConfig cfg) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("group", cfg.getGroupName());
        group.put("consumeMode", cfg.isConsumeBroadcastEnable() ? "BROADCAST" : "CLUSTER");
        group.put("consumeEnable", cfg.isConsumeEnable());
        group.put("retryMaxTimes", cfg.getRetryMaxTimes());
        group.put("retryQueueNums", cfg.getRetryQueueNums());
        return group;
    }

    private Map<String, Object> messageToMap(MessageExt msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("msgId", msg.getMsgId());
        m.put("topic", msg.getTopic());
        m.put("tags", msg.getTags());
        m.put("keys", msg.getKeys());
        m.put("queueId", msg.getQueueId());
        m.put("queueOffset", msg.getQueueOffset());
        m.put("reconsumeTimes", msg.getReconsumeTimes());
        m.put("bornTimestamp", msg.getBornTimestamp());
        m.put("storeTimestamp", msg.getStoreTimestamp());
        m.put("bornHost", String.valueOf(msg.getBornHost()));
        m.put("storeHost", String.valueOf(msg.getStoreHost()));
        if (msg.getBody() != null) {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            m.put("body", body.length() > 4096 ? body.substring(0, 4096) + "...(truncated)" : body);
            m.put("bodyLength", msg.getBody().length);
        }
        m.put("properties", msg.getProperties());
        return m;
    }

    private Map<String, Object> connectionToMap(Connection c, String group) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("clientId", c.getClientId());
        client.put("clientAddr", c.getClientAddr());
        client.put("language", c.getLanguage() != null ? c.getLanguage().name() : null);
        client.put("version", c.getVersion());
        client.put("group", group);
        return client;
    }

    private String resolveBrokerAddr(AdminClientHelper admin, String brokerName) throws Exception {
        ClusterInfo clusterInfo = admin.getClusterInfo();
        if (clusterInfo.getBrokerAddrTable() != null) {
            BrokerData bd = clusterInfo.getBrokerAddrTable().get(brokerName);
            if (bd != null && bd.selectBrokerAddr() != null) {
                return bd.selectBrokerAddr();
            }
        }
        if (brokerName.contains(":")) {
            return brokerName; // already an address
        }
        throw new IllegalArgumentException("Broker not found in cluster: " + brokerName);
    }

    // ---- argument helpers -------------------------------------------------------------

    private static String toAclSubject(String username) {
        return username.contains(":") ? username : "User:" + username;
    }

    private static List<String> splitActions(String actions) {
        return Arrays.stream(actions.split("[|,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String getString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static String requireString(Map<String, Object> args, String key) {
        String value = getString(args, key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value;
    }

    private static Integer getInt(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Long getLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static long requireLong(Map<String, Object> args, String key) {
        Long value = getLong(args, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value;
    }
}
