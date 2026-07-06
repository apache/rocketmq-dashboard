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
package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.service.ClientService;
import org.apache.rocketmq.dashboard.service.UnifiedClientService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unified Client REST API Controller.
 *
 * <p>Provides endpoints for client instance management across both Remoting
 * and gRPC protocols. This controller integrates both {@link UnifiedClientService}
 * (dual-protocol unified view) and {@link ClientService} (extended management
 * operations like kill, config update, diagnostics).</p>
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/client/list</td><td>List all clients (unified view)</td></tr>
 *   <tr><td>GET</td><td>/api/client/{clientId}</td><td>Get client details</td></tr>
 *   <tr><td>GET</td><td>/api/client/{clientId}/subscriptions</td><td>Get client subscriptions</td></tr>
 *   <tr><td>GET</td><td>/api/client/byProtocol</td><td>List clients by protocol</td></tr>
 *   <tr><td>GET</td><td>/api/client/byType</td><td>List clients by type</td></tr>
 *   <tr><td>GET</td><td>/api/client/byCluster</td><td>List clients by cluster</td></tr>
 *   <tr><td>GET</td><td>/api/client/connected</td><td>Get connected clients for broker</td></tr>
 *   <tr><td>GET</td><td>/api/client/idle</td><td>Get idle clients</td></tr>
 *   <tr><td>GET</td><td>/api/client/issues</td><td>Get clients with issues</td></tr>
 *   <tr><td>POST</td><td>/api/client/{clientId}/kill</td><td>Kill client connection</td></tr>
 *   <tr><td>POST</td><td>/api/client/{clientId}/config</td><td>Update client config</td></tr>
 *   <tr><td>GET</td><td>/api/client/channels</td><td>Get available channels</td></tr>
 * </table>
 */
@Controller
@RequestMapping("/api/client")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    @Autowired
    private UnifiedClientService unifiedClientService;

    @Autowired
    private ClientService clientService;

    /**
     * GET /api/client/list - List all client instances (unified dual-protocol view).
     *
     * <p>Query parameters:</p>
     * <ul>
     *   <li>topic (optional) - filter by topic</li>
     *   <li>group (optional) - filter by consumer group</li>
     * </ul>
     *
     * @param topic optional topic filter
     * @param group optional consumer group filter
     * @return JsonResult containing list of ClientInstance
     */
    @GetMapping("/list")
    @ResponseBody
    public Object listClients(
        @RequestParam(required = false) String topic,
        @RequestParam(required = false) String group) {
        try {
            Optional<String> topicOpt = topic != null && !topic.trim().isEmpty()
                ? Optional.of(topic.trim()) : Optional.empty();
            Optional<String> groupOpt = group != null && !group.trim().isEmpty()
                ? Optional.of(group.trim()) : Optional.empty();

            List<ClientInstance> clients = unifiedClientService.listAllClients(topicOpt, groupOpt);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to list clients", e);
            return new JsonResult<>(1, "Failed to list clients: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/{clientId} - Get detailed information for a specific client.
     *
     * @param clientId the unique client identifier
     * @return JsonResult containing ClientInstance details
     */
    @GetMapping("/{clientId}")
    @ResponseBody
    public Object getClient(@PathVariable String clientId) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                return new JsonResult<>(1, "Client ID cannot be empty");
            }

            Optional<ClientInstance> client = unifiedClientService.describeClient(clientId);
            if (client.isPresent()) {
                return new JsonResult<>(client.get());
            } else {
                return new JsonResult<>(1, "Client '" + clientId + "' not found");
            }
        } catch (Exception e) {
            log.error("Failed to get client: {}", clientId, e);
            return new JsonResult<>(1, "Failed to get client: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/{clientId}/subscriptions - Get subscription info for a client.
     *
     * @param clientId the unique client identifier
     * @return JsonResult containing list of SubscriptionInfo
     */
    @GetMapping("/{clientId}/subscriptions")
    @ResponseBody
    public Object getClientSubscriptions(@PathVariable String clientId) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                return new JsonResult<>(1, "Client ID cannot be empty");
            }

            List<SubscriptionInfo> subscriptions = unifiedClientService.getClientSubscriptions(clientId);
            return new JsonResult<>(subscriptions);
        } catch (Exception e) {
            log.error("Failed to get subscriptions for client: {}", clientId, e);
            return new JsonResult<>(1, "Failed to get subscriptions: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/byProtocol - List clients by protocol type.
     *
     * @param protocol protocol type (REMOTING or GRPC)
     * @return JsonResult containing filtered client list
     */
    @GetMapping("/byProtocol")
    @ResponseBody
    public Object listClientsByProtocol(@RequestParam String protocol) {
        try {
            if (protocol == null || protocol.trim().isEmpty()) {
                return new JsonResult<>(1, "Protocol parameter is required");
            }

            List<ClientInstance> clients = clientService.listClientsByProtocol(protocol);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to list clients by protocol: {}", protocol, e);
            return new JsonResult<>(1, "Failed to list clients by protocol: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/byType - List clients by client type.
     *
     * @param clientType client type (PRODUCER or CONSUMER)
     * @return JsonResult containing filtered client list
     */
    @GetMapping("/byType")
    @ResponseBody
    public Object listClientsByType(@RequestParam String clientType) {
        try {
            if (clientType == null || clientType.trim().isEmpty()) {
                return new JsonResult<>(1, "Client type parameter is required");
            }

            List<ClientInstance> clients = clientService.listClientsByType(clientType);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to list clients by type: {}", clientType, e);
            return new JsonResult<>(1, "Failed to list clients by type: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/byCluster - List clients by cluster name.
     *
     * @param clusterName the cluster name
     * @return JsonResult containing filtered client list
     */
    @GetMapping("/byCluster")
    @ResponseBody
    public Object listClientsByCluster(@RequestParam String clusterName) {
        try {
            if (clusterName == null || clusterName.trim().isEmpty()) {
                return new JsonResult<>(1, "Cluster name parameter is required");
            }

            List<ClientInstance> clients = clientService.listClientsByCluster(clusterName);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to list clients by cluster: {}", clusterName, e);
            return new JsonResult<>(1, "Failed to list clients by cluster: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/connected - Get clients connected to a specific broker.
     *
     * @param brokerAddress the broker address
     * @return JsonResult containing connected client list
     */
    @GetMapping("/connected")
    @ResponseBody
    public Object getConnectedClients(@RequestParam String brokerAddress) {
        try {
            if (brokerAddress == null || brokerAddress.trim().isEmpty()) {
                return new JsonResult<>(1, "Broker address parameter is required");
            }

            List<ClientInstance> clients = clientService.getConnectedClients(brokerAddress);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to get connected clients for broker: {}", brokerAddress, e);
            return new JsonResult<>(1, "Failed to get connected clients: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/idle - Get idle clients based on time threshold.
     *
     * @param threshold idle time threshold in milliseconds (default: 300000 = 5 minutes)
     * @return JsonResult containing idle client list
     */
    @GetMapping("/idle")
    @ResponseBody
    public Object getIdleClients(@RequestParam(defaultValue = "300000") long threshold) {
        try {
            List<ClientInstance> clients = clientService.getIdleClients(threshold);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to get idle clients", e);
            return new JsonResult<>(1, "Failed to get idle clients: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/issues - Get clients with specific issues.
     *
     * @param issueType the issue type to filter by
     * @return JsonResult containing clients with issues
     */
    @GetMapping("/issues")
    @ResponseBody
    public Object getClientsWithIssue(@RequestParam String issueType) {
        try {
            if (issueType == null || issueType.trim().isEmpty()) {
                return new JsonResult<>(1, "Issue type parameter is required");
            }

            List<ClientInstance> clients = clientService.getClientsWithIssue(issueType);
            return new JsonResult<>(clients);
        } catch (Exception e) {
            log.error("Failed to get clients with issue: {}", issueType, e);
            return new JsonResult<>(1, "Failed to get clients with issue: " + e.getMessage());
        }
    }

    /**
     * POST /api/client/{clientId}/kill - Kill a client connection.
     *
     * @param clientId the client ID to kill
     * @param request  request body containing "reason" field
     * @return JsonResult confirming kill operation
     */
    @PostMapping("/{clientId}/kill")
    @ResponseBody
    public Object killClient(
        @PathVariable String clientId,
        @RequestBody(required = false) Map<String, Object> request) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                return new JsonResult<>(1, "Client ID cannot be empty");
            }

            String reason = null;
            if (request != null && request.containsKey("reason")) {
                reason = (String) request.get("reason");
            }

            boolean success = clientService.killClient(clientId, reason);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("clientId", clientId);
            result.put("message", success
                ? "Client connection killed successfully"
                : "Failed to kill client connection");
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to kill client: {}", clientId, e);
            return new JsonResult<>(1, "Failed to kill client: " + e.getMessage());
        }
    }

    /**
     * POST /api/client/{clientId}/config - Update a client configuration.
     *
     * @param clientId the client ID
     * @param request  request body containing "configKey" and "configValue"
     * @return JsonResult confirming config update
     */
    @PostMapping("/{clientId}/config")
    @ResponseBody
    public Object updateClientConfig(
        @PathVariable String clientId,
        @RequestBody Map<String, Object> request) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                return new JsonResult<>(1, "Client ID cannot be empty");
            }
            if (request == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }

            String configKey = (String) request.get("configKey");
            String configValue = request.get("configValue") != null
                ? request.get("configValue").toString() : null;

            if (configKey == null || configKey.trim().isEmpty()) {
                return new JsonResult<>(1, "configKey is required");
            }

            boolean success = clientService.updateClientConfig(clientId, configKey, configValue);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("clientId", clientId);
            result.put("configKey", configKey);
            result.put("message", success
                ? "Client config updated successfully"
                : "Failed to update client config");
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to update client config: {}", clientId, e);
            return new JsonResult<>(1, "Failed to update client config: " + e.getMessage());
        }
    }

    /**
     * GET /api/client/channels - Get available client collection channels.
     *
     * <p>Returns which channels (REMOTING, GRPC) are currently available
     * for client data collection. Frontend can use this to conditionally
     * show/hide protocol indicators.</p>
     *
     * @return JsonResult containing available channel list
     */
    @GetMapping("/channels")
    @ResponseBody
    public Object getAvailableChannels() {
        try {
            List<String> channels = unifiedClientService.getAvailableChannels();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("channels", channels);
            result.put("remotingAvailable", channels.contains("REMOTING"));
            result.put("grpcAvailable", channels.contains("GRPC"));
            result.put("message", channels.isEmpty()
                ? "No client collection channels available"
                : "Available channels: " + String.join(", ", channels));
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to get available channels", e);
            return new JsonResult<>(1, "Failed to get available channels: " + e.getMessage());
        }
    }
}