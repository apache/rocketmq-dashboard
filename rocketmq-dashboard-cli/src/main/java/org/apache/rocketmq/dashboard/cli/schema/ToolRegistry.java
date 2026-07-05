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
package org.apache.rocketmq.dashboard.cli.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Singleton registry that holds all tool definitions as the single source of truth.
 * Used by both the CLI (picocli command generation) and the MCP server (tool listing).
 */
public final class ToolRegistry {

    private static final ToolRegistry INSTANCE = new ToolRegistry();

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    private ToolRegistry() {
        registerAll();
    }

    public static ToolRegistry getInstance() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------------------
    // Query methods
    // ---------------------------------------------------------------------------

    /** Returns an unmodifiable list of all registered tools. */
    public List<ToolDefinition> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /** Looks up a single tool by its exact name (e.g. "rmq.topic.create"). */
    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    /** Returns all tools that belong to the given resource domain. */
    public List<ToolDefinition> getToolsByResource(String resource) {
        return tools.values().stream()
                .filter(t -> t.getResource().equalsIgnoreCase(resource))
                .collect(Collectors.toList());
    }

    /** Returns all tools at the specified risk level. */
    public List<ToolDefinition> getToolsByRiskLevel(RiskLevel level) {
        return tools.values().stream()
                .filter(t -> t.getRiskLevel() == level)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------------
    // Convenience helpers
    // ---------------------------------------------------------------------------

    private static ParamSchema p(String name, String type, boolean required, String description) {
        return ParamSchema.builder().name(name).type(type).required(required).description(description).build();
    }

    private static ParamSchema p(String name, String type, boolean required, String description, String defaultValue) {
        return ParamSchema.builder().name(name).type(type).required(required)
                .description(description).defaultValue(defaultValue).build();
    }

    private static ParamSchema p(String name, String type, boolean required, String description,
                                  String defaultValue, String... allowedValues) {
        return ParamSchema.builder().name(name).type(type).required(required)
                .description(description).defaultValue(defaultValue).allowedValues(allowedValues).build();
    }

    private ToolDefinition def(String resource, String verb, RiskLevel riskLevel,
                               String description, String returnType, ParamSchema... params) {
        String name = "rmq." + resource + "." + verb;
        return ToolDefinition.builder()
                .name(name)
                .resource(resource)
                .verb(verb)
                .riskLevel(riskLevel)
                .description(description)
                .params(Arrays.asList(params))
                .returnType(returnType)
                .build();
    }

    // ---------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------

    private void registerAll() {
        registerCluster();
        registerNamespace();
        registerTopic();
        registerGroup();
        registerMessage();
        registerClient();
        registerAcl();
        registerBroker();
        registerMetrics();
        registerCapabilities();
    }

    private void register(ToolDefinition td) {
        tools.put(td.getName(), td);
    }

    // ---- cluster -------------------------------------------------------------

    private void registerCluster() {
        register(def("cluster", "list", RiskLevel.L1,
                "List all clusters in the RocketMQ deployment.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to.")
        ));

        register(def("cluster", "describe", RiskLevel.L1,
                "Get detailed information about a specific cluster.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to.")
        ));
    }

    // ---- namespace -----------------------------------------------------------

    private void registerNamespace() {
        register(def("namespace", "list", RiskLevel.L1,
                "List all namespaces.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to.")
        ));

        register(def("namespace", "create", RiskLevel.L2,
                "Create a new namespace.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("name", "STRING", true, "Namespace name to create.")
        ));

        register(def("namespace", "delete", RiskLevel.L3,
                "Delete an existing namespace.",
                "VOID",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("name", "STRING", true, "Namespace name to delete.")
        ));
    }

    // ---- topic ---------------------------------------------------------------

    private void registerTopic() {
        register(def("topic", "list", RiskLevel.L1,
                "List all topics, optionally filtered by namespace or topic type.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("namespace", "STRING", false, "Optional namespace filter."),
                p("topicType", "ENUM", false, "Topic type filter.",
                        null, "NORMAL", "FIFO", "DELAY", "TRANSACTION")
        ));

        register(def("topic", "describe", RiskLevel.L1,
                "Get detailed information about a specific topic.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("topic", "STRING", true, "Topic name."),
                p("namespace", "STRING", false, "Optional namespace.")
        ));

        register(def("topic", "create", RiskLevel.L2,
                "Create a new topic with the specified configuration.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("topic", "STRING", true, "Topic name to create."),
                p("topicType", "ENUM", false, "Topic type.", "NORMAL",
                        "NORMAL", "FIFO", "DELAY", "TRANSACTION"),
                p("readQueueNums", "INT", false, "Number of read queues.", "8"),
                p("writeQueueNums", "INT", false, "Number of write queues.", "8"),
                p("namespace", "STRING", false, "Optional namespace.")
        ));

        register(def("topic", "update", RiskLevel.L2,
                "Update configuration of an existing topic.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("topic", "STRING", true, "Topic name to update."),
                p("readQueueNums", "INT", false, "New number of read queues."),
                p("writeQueueNums", "INT", false, "New number of write queues."),
                p("perm", "INT", false, "Permission flags (e.g. 6 for read-write).")
        ));

        register(def("topic", "delete", RiskLevel.L3,
                "Delete a topic and all its messages.",
                "VOID",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("topic", "STRING", true, "Topic name to delete."),
                p("namespace", "STRING", false, "Optional namespace.")
        ));
    }

    // ---- group ---------------------------------------------------------------

    private void registerGroup() {
        register(def("group", "list", RiskLevel.L1,
                "List all consumer groups, optionally filtered by namespace or topic.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("namespace", "STRING", false, "Optional namespace filter."),
                p("topic", "STRING", false, "Optional topic filter.")
        ));

        register(def("group", "describe", RiskLevel.L1,
                "Get detailed information about a specific consumer group.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("group", "STRING", true, "Consumer group name."),
                p("namespace", "STRING", false, "Optional namespace.")
        ));

        register(def("group", "create", RiskLevel.L2,
                "Create a new consumer group.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("group", "STRING", true, "Consumer group name to create."),
                p("consumeMode", "ENUM", false, "Consumption mode.", "CLUSTER",
                        "CLUSTER", "BROADCAST"),
                p("namespace", "STRING", false, "Optional namespace.")
        ));

        register(def("group", "update", RiskLevel.L2,
                "Update configuration of an existing consumer group.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("group", "STRING", true, "Consumer group name to update."),
                p("consumeMode", "ENUM", false, "New consumption mode.",
                        null, "CLUSTER", "BROADCAST"),
                p("retryMaxTimes", "INT", false, "Maximum retry times.")
        ));

        register(def("group", "reset-offset", RiskLevel.L2,
                "Reset the consumer offset for a group-topic pair to the specified timestamp.",
                "VOID",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("group", "STRING", true, "Consumer group name."),
                p("topic", "STRING", true, "Topic to reset offset for."),
                p("timestamp", "LONG", true, "Timestamp to reset the offset to (milliseconds since epoch).")
        ));

        register(def("group", "delete", RiskLevel.L3,
                "Delete a consumer group.",
                "VOID",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("group", "STRING", true, "Consumer group name to delete."),
                p("namespace", "STRING", false, "Optional namespace.")
        ));
    }

    // ---- message -------------------------------------------------------------

    private void registerMessage() {
        register(def("message", "query-by-id", RiskLevel.L1,
                "Query a message by its message ID.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("msgId", "STRING", true, "Message ID to query."),
                p("topic", "STRING", true, "Topic the message belongs to.")
        ));

        register(def("message", "query-by-time", RiskLevel.L1,
                "Query messages within a time range.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("topic", "STRING", true, "Topic to query messages from."),
                p("beginTime", "LONG", true, "Start timestamp (milliseconds since epoch)."),
                p("endTime", "LONG", true, "End timestamp (milliseconds since epoch)."),
                p("maxNum", "INT", false, "Maximum number of messages to return.", "32")
        ));

        register(def("message", "resend", RiskLevel.L2,
                "Resend a message to a consumer group.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("msgId", "STRING", true, "Message ID to resend."),
                p("topic", "STRING", true, "Topic the message belongs to."),
                p("group", "STRING", true, "Target consumer group.")
        ));
    }

    // ---- client --------------------------------------------------------------

    private void registerClient() {
        register(def("client", "list", RiskLevel.L1,
                "List all connected clients, optionally filtered by topic or consumer group.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("topic", "STRING", false, "Optional topic filter."),
                p("group", "STRING", false, "Optional consumer group filter.")
        ));

        register(def("client", "describe", RiskLevel.L1,
                "Get detailed information about a specific client connection.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("clientId", "STRING", true, "Client ID to describe.")
        ));
    }

    // ---- acl -----------------------------------------------------------------

    private void registerAcl() {
        register(def("acl", "list", RiskLevel.L1,
                "List all ACL policies, optionally filtered by namespace.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("namespace", "STRING", false, "Optional namespace filter.")
        ));

        register(def("acl", "create", RiskLevel.L2,
                "Create a new ACL policy.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("username", "STRING", true, "Username for the ACL policy."),
                p("resource", "STRING", true, "Resource pattern (e.g. topic name)."),
                p("actions", "STRING", true, "Allowed actions (e.g. PUB, SUB, PUB|SUB)."),
                p("decision", "ENUM", false, "Allow or deny.", "ALLOW",
                        "ALLOW", "DENY")
        ));

        register(def("acl", "update", RiskLevel.L2,
                "Update an existing ACL policy.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("policyId", "STRING", true, "ACL policy ID to update."),
                p("actions", "STRING", false, "Updated actions (optional)."),
                p("decision", "ENUM", false, "Updated decision (optional).",
                        null, "ALLOW", "DENY")
        ));

        register(def("acl", "delete", RiskLevel.L3,
                "Delete an ACL policy.",
                "VOID",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("policyId", "STRING", true, "ACL policy ID to delete.")
        ));
    }

    // ---- broker --------------------------------------------------------------

    private void registerBroker() {
        register(def("broker", "list", RiskLevel.L1,
                "List all brokers in the cluster.",
                "LIST",
                p("cluster", "STRING", true, "Cluster name or address to connect to.")
        ));

        register(def("broker", "describe", RiskLevel.L1,
                "Get detailed information about a specific broker.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("brokerName", "STRING", true, "Broker name to describe.")
        ));

        register(def("broker", "config", RiskLevel.L2,
                "Get or update broker configuration. Omit configValue to read.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("brokerName", "STRING", true, "Broker name."),
                p("configKey", "STRING", false, "Configuration key to read or update."),
                p("configValue", "STRING", false, "New value (omit to read current value).")
        ));
    }

    // ---- metrics -------------------------------------------------------------

    private void registerMetrics() {
        register(def("metrics", "query", RiskLevel.L1,
                "Query metrics for the specified target.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to."),
                p("metricType", "ENUM", true, "Type of metrics to query.",
                        null, "cluster", "broker", "topic", "consumer", "client", "system"),
                p("targetName", "STRING", false, "Target name (e.g. topic name, broker name).")
        ));
    }

    // ---- capabilities --------------------------------------------------------

    private void registerCapabilities() {
        register(def("capabilities", "detect", RiskLevel.L1,
                "Detect the capabilities and features supported by the connected cluster. MCP only — no CLI equivalent.",
                "OBJECT",
                p("cluster", "STRING", true, "Cluster name or address to connect to.")
        ));
    }
}
