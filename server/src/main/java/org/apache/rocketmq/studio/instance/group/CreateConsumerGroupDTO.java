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
package org.apache.rocketmq.studio.instance.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;

@Data
public class CreateConsumerGroupDTO {
    @NotBlank(message = "name is required")
    private String name;
    private String namespace;
    private String clusterId;
    private SubscriptionMode subscriptionMode;
    private ConsumeType consumeType;
    private String subscriptionDataType;
    private String deliveryOrderType;
    @PositiveOrZero(message = "retryMaxTimes must be zero or positive")
    private Integer retryMaxTimes;
    @PositiveOrZero(message = "delaySeconds must be zero or positive")
    private Integer delaySeconds;

    private Long instanceId;

    public ConsumerGroupVO toConsumerGroupVO() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setInstanceId(instanceId);
        group.setName(name);
        group.setNamespace(namespace);
        group.setClusterId(clusterId);
        group.setSubscriptionMode(subscriptionMode);
        group.setConsumeType(consumeType);
        group.setSubscriptionDataType(subscriptionDataType);
        group.setDeliveryOrderType(deliveryOrderType);
        if (retryMaxTimes != null) {
            group.setRetryMaxTimes(retryMaxTimes);
        }
        if (delaySeconds != null) {
            group.setDelaySeconds(delaySeconds);
        }
        return group;
    }
}
