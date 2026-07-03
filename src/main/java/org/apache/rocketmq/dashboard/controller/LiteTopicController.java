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

import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.service.LiteTopicService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LiteTopic REST API Controller.
 *
 * <p>Provides endpoints for RocketMQ 5.0 LiteTopic management including
 * session listing, TTL extension, and quota monitoring. LiteTopic is a
 * lightweight, auto-expiring topic feature only available in V5 Proxy
 * architecture.</p>
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/liteTopic/list</td><td>List LiteTopics with optional filters</td></tr>
 *   <tr><td>GET</td><td>/api/liteTopic/session/{sessionId}</td><td>Get session details</td></tr>
 *   <tr><td>POST</td><td>/api/liteTopic/extendTTL</td><td>Extend LiteTopic TTL</td></tr>
 *   <tr><td>GET</td><td>/api/liteTopic/quota</td><td>Get quota information</td></tr>
 *   <tr><td>GET</td><td>/api/liteTopic/capability</td><td>Check LiteTopic support status</td></tr>
 * </table>
 */
@Controller
@RequestMapping("/api/liteTopic")
public class LiteTopicController {

    private static final Logger log = LoggerFactory.getLogger(LiteTopicController.class);

    @Autowired
    private LiteTopicService liteTopicService;

    /**
     * GET /api/liteTopic/list - List LiteTopics with optional pattern and namespace filters.
     *
     * <p>Query parameters:</p>
     * <ul>
     *   <li>pattern (optional) - topic pattern filter</li>
     *   <li>namespace (optional) - namespace scope</li>
     * </ul>
     *
     * @param pattern   topic pattern filter (optional)
     * @param namespace namespace scope (optional)
     * @return JsonResult containing list of LiteTopic summaries
     */
    @GetMapping("/list")
    @ResponseBody
    public Object listLiteTopics(
        @RequestParam(required = false) String pattern,
        @RequestParam(required = false) String namespace) {
        try {
            Optional<String> nsOpt = namespace != null && !namespace.trim().isEmpty()
                ? Optional.of(namespace.trim())
                : Optional.empty();

            List<LiteTopicSummary> topics = liteTopicService.listLiteTopics(pattern, nsOpt);
            return new JsonResult<>(topics);
        } catch (Exception e) {
            log.error("Failed to list LiteTopics", e);
            return new JsonResult<>(1, "Failed to list LiteTopics: " + e.getMessage());
        }
    }

    /**
     * GET /api/liteTopic/session/{sessionId} - Get a specific LiteTopic session.
     *
     * @param sessionId the session identifier
     * @return JsonResult containing LiteTopic session details
     */
    @GetMapping("/session/{sessionId}")
    @ResponseBody
    public Object getLiteTopicSession(@PathVariable String sessionId) {
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return new JsonResult<>(1, "Session ID cannot be empty");
            }

            LiteTopicSession session = liteTopicService.getLiteTopicSession(sessionId);
            return new JsonResult<>(session);
        } catch (UnsupportedOperationException e) {
            log.warn("LiteTopic session not supported: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("supported", false);
            result.put("message", "LiteTopic sessions require RocketMQ 5.0+ with Proxy and RIP-2 gRPC interface");
            return new JsonResult<>(2, result, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get LiteTopic session: {}", sessionId, e);
            return new JsonResult<>(1, "Failed to get LiteTopic session: " + e.getMessage());
        }
    }

    /**
     * POST /api/liteTopic/extendTTL - Extend the TTL of LiteTopics matching a pattern.
     *
     * <p>Request body (JSON):</p>
     * <pre>{@code
     * {
     *   "topicPattern": "order-*",
     *   "newTTL": 3600000
     * }
     * }</pre>
     *
     * @param request the TTL extension request containing topicPattern and newTTL
     * @return JsonResult confirming TTL extension
     */
    @PostMapping("/extendTTL")
    @ResponseBody
    public Object extendLiteTopicTTL(@RequestBody Map<String, Object> request) {
        try {
            if (request == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }

            String topicPattern = (String) request.get("topicPattern");
            Object ttlObj = request.get("newTTL");

            if (topicPattern == null || topicPattern.trim().isEmpty()) {
                return new JsonResult<>(1, "topicPattern is required");
            }
            if (ttlObj == null) {
                return new JsonResult<>(1, "newTTL is required");
            }

            long newTTL;
            if (ttlObj instanceof Number) {
                newTTL = ((Number) ttlObj).longValue();
            } else {
                try {
                    newTTL = Long.parseLong(ttlObj.toString());
                } catch (NumberFormatException e) {
                    return new JsonResult<>(1, "newTTL must be a valid number");
                }
            }

            liteTopicService.extendLiteTopicTTL(topicPattern, newTTL);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("topicPattern", topicPattern);
            result.put("newTTL", newTTL);
            result.put("message", "LiteTopic TTL extended successfully");
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid TTL extension request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warn("LiteTopic TTL extension not supported: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("supported", false);
            result.put("message", "LiteTopic TTL extension requires RocketMQ 5.0+ with Proxy and RIP-2 gRPC interface");
            return new JsonResult<>(2, result, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to extend LiteTopic TTL", e);
            return new JsonResult<>(1, "Failed to extend LiteTopic TTL: " + e.getMessage());
        }
    }

    /**
     * GET /api/liteTopic/quota - Get LiteTopic quota information.
     *
     * <p>Query parameters:</p>
     * <ul>
     *   <li>namespace (optional) - namespace scope for quota</li>
     * </ul>
     *
     * @param namespace namespace scope (optional)
     * @return JsonResult containing LiteTopic quota information
     */
    @GetMapping("/quota")
    @ResponseBody
    public Object getLiteTopicQuota(@RequestParam(required = false) String namespace) {
        try {
            Optional<String> nsOpt = namespace != null && !namespace.trim().isEmpty()
                ? Optional.of(namespace.trim())
                : Optional.empty();

            LiteTopicQuota quota = liteTopicService.getLiteTopicQuota(nsOpt);
            return new JsonResult<>(quota);
        } catch (UnsupportedOperationException e) {
            log.warn("LiteTopic quota not supported: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("supported", false);
            result.put("message", "LiteTopic quotas require RocketMQ 5.0+ with Proxy and RIP-2 gRPC interface");
            return new JsonResult<>(2, result, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get LiteTopic quota", e);
            return new JsonResult<>(1, "Failed to get LiteTopic quota: " + e.getMessage());
        }
    }

    /**
     * GET /api/liteTopic/capability - Check if LiteTopic management is supported.
     *
     * @return JsonResult with LiteTopic capability status
     */
    @GetMapping("/capability")
    @ResponseBody
    public Object getLiteTopicCapability() {
        Map<String, Object> capability = new LinkedHashMap<>();
        boolean supported = liteTopicService.isLiteTopicSupported();
        capability.put("liteTopicSupported", supported);
        capability.put("message", supported
            ? "LiteTopic management is fully supported in this cluster"
            : "LiteTopic management requires RocketMQ 5.0+ with Proxy architecture");
        return new JsonResult<>(capability);
    }
}