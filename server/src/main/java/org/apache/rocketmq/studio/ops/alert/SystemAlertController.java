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
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-alerts")
@RequiredArgsConstructor
public class SystemAlertController {

    private final AlertService alertService;
    private final NotificationOutboxService notificationOutboxService;

    @GetMapping
    public Result<List<SystemAlertVO>> listAlerts(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) AlertDomain domain,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String transition) {
        return Result.ok(alertService.listAlerts(level, domain, instanceId, transition));
    }

    @GetMapping("/page")
    public Result<PageResult<SystemAlertVO>> listAlertsPage(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) AlertDomain domain,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String transition,
            @RequestParam(required = false) String labelKey,
            @RequestParam(required = false) String labelValue,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) Boolean notificationSuppressed,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(alertService.listAlerts(level, domain, instanceId, transition,
                labelKey, labelValue, from, to, page, pageSize, notificationSuppressed));
    }

    @GetMapping("/{id}/related")
    public Result<List<SystemAlertVO>> listRelatedAlerts(@PathVariable Long id) {
        return Result.ok(alertService.findRelatedAlerts(id));
    }

    @GetMapping("/{id}/deliveries")
    public Result<List<NotificationDeliveryVO>> listDeliveries(@PathVariable Long id) {
        return Result.ok(notificationOutboxService.listDeliveries(id));
    }

    @GetMapping("/deliveries/page")
    public Result<PageResult<NotificationDeliveryPageVO>> listDeliveriesPage(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(notificationOutboxService.listDeliveries(channel, status, instanceId, page, pageSize));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public Result<Void> retryFailedDelivery(@PathVariable Long deliveryId) {
        notificationOutboxService.retryFailedDelivery(deliveryId);
        return Result.ok();
    }

    @PostMapping("/deliveries/retry")
    public Result<NotificationDeliveryBulkRetryResult> retryFailedDeliveries(
            @RequestBody(required = false) List<Long> deliveryIds) {
        return Result.ok(notificationOutboxService.retryFailedDeliveries(deliveryIds));
    }

    @PostMapping("/acknowledge")
    public Result<SystemAlertVO> acknowledgeAlert(
            @Valid @RequestBody(required = false) AcknowledgeSystemAlertDTO request) {
        requireAcknowledgeRequest(request);
        return Result.ok(alertService.acknowledgeAlert(request.getId()));
    }

    @PostMapping("/clear-acknowledged")
    public Result<Map<String, Integer>> clearAcknowledged() {
        int cleared = alertService.clearAcknowledged();
        return Result.ok(Map.of("cleared", cleared));
    }

    private void requireAcknowledgeRequest(AcknowledgeSystemAlertDTO request) {
        if (request == null) {
            throw new BusinessException(400, "System alert acknowledge request is required");
        }
    }
}
