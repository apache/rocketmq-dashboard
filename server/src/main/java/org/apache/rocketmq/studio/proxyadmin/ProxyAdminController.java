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
package org.apache.rocketmq.studio.proxyadmin;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RIP-2 ProxyAdminService surfaces that go beyond client listing: route observation
 * (topology + change events) and POP / batch consume diagnostics. All endpoints resolve
 * the target proxy admin endpoint from a PROXY instance (its endpoint must be the proxy
 * admin gRPC address) and delegate to {@link ProxyAdminClient}.
 */
@Slf4j
@RestController
@RequestMapping("/api/proxy-admin")
@RequiredArgsConstructor
public class ProxyAdminController {

    private final ProxyAdminClient proxyAdminClient;
    private final InstanceRepository instanceRepository;

    @GetMapping("/route-topology")
    public Result<ProxyAdminDiagnosticsVO.RouteTopology> routeTopology(
            @RequestParam String instanceId,
            @RequestParam(required = false) String topic) {
        return Result.ok(proxyAdminClient.describeRouteTopology(
                resolveProxyAdminEndpoint(instanceId), topic));
    }

    @GetMapping("/pop-receipt-handles")
    public Result<ProxyAdminDiagnosticsVO.PopReceiptHandles> popReceiptHandles(
            @RequestParam String instanceId,
            @RequestParam String group,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return Result.ok(proxyAdminClient.describePopReceiptHandles(
                resolveProxyAdminEndpoint(instanceId), group, topic, pageNum, pageSize));
    }

    @GetMapping("/batch-consume-diagnostics")
    public Result<ProxyAdminDiagnosticsVO.BatchConsumeDiagnostics> batchConsumeDiagnostics(
            @RequestParam String instanceId,
            @RequestParam String group,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return Result.ok(proxyAdminClient.describeBatchConsumeDiagnostics(
                resolveProxyAdminEndpoint(instanceId), group, topic, clientId, pageNum, pageSize));
    }

    /**
     * Collects route-change events over a bounded window (1-10s, default 3s). The snapshot
     * replay emitted on subscribe is included, so the response always reflects current route
     * state even when no change occurs during the window.
     */
    @GetMapping("/route-events")
    public Result<List<ProxyAdminDiagnosticsVO.RouteEvent>> routeEvents(
            @RequestParam String instanceId,
            @RequestParam(required = false) String topics,
            @RequestParam(required = false, defaultValue = "3") long windowSeconds,
            @RequestParam(required = false, defaultValue = "50") int maxEvents) {
        List<String> topicList = StringUtils.hasText(topics)
                ? Arrays.stream(topics.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of();
        return Result.ok(proxyAdminClient.collectRouteEvents(
                resolveProxyAdminEndpoint(instanceId), topicList, windowSeconds, maxEvents));
    }

    private String resolveProxyAdminEndpoint(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (instance.getType() != InstanceType.PROXY) {
            throw new BusinessException(400,
                    "RIP-2 diagnostics require a PROXY instance, got " + instance.getType());
        }
        if (!StringUtils.hasText(instance.getEndpoint())) {
            throw new BusinessException(400, "PROXY instance has no endpoint: " + instanceId);
        }
        return instance.getEndpoint().trim();
    }
}
