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
package org.apache.rocketmq.studio.ops.ai.tool.contract.alert;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.ops.alert.NotificationDeliveryPageVO;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDeliveryItem(
        Long id,
        Long alertId,
        String channel,
        String status,
        int attemptCount,
        String nextAttemptAt,
        String lastError,
        String deliveredAt,
        String createdAt,
        String alertTitle,
        String alertDomain,
        String transition,
        String instanceId) {

    public static NotificationDeliveryItem from(NotificationDeliveryPageVO delivery) {
        return new NotificationDeliveryItem(
                delivery.getId(),
                delivery.getAlertId(),
                delivery.getChannel(),
                delivery.getStatus() == null ? null : delivery.getStatus().name(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt() == null ? null : delivery.getNextAttemptAt().toString(),
                delivery.getLastError(),
                delivery.getDeliveredAt() == null ? null : delivery.getDeliveredAt().toString(),
                delivery.getCreatedAt() == null ? null : delivery.getCreatedAt().toString(),
                delivery.getAlertTitle(),
                delivery.getAlertDomain() == null ? null : delivery.getAlertDomain().name(),
                delivery.getTransition(),
                delivery.getInstanceId());
    }
}
