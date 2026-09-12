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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;

public record GroupInput(
        String cluster,
        String group,
        String namespace,
        SubscriptionMode subscriptionMode,
        ConsumeType consumeType,
        String subscriptionDataType,
        String deliveryOrderType,
        Integer retryMaxTimes,
        Integer delaySeconds) {

    public ConsumerGroupVO toConsumerGroupVO() {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName(group);
        vo.setNamespace(namespace);
        vo.setInstanceId(cluster);
        vo.setSubscriptionDataType(subscriptionDataType);
        vo.setDeliveryOrderType(deliveryOrderType);
        if (retryMaxTimes != null) {
            vo.setRetryMaxTimes(retryMaxTimes);
        }
        if (delaySeconds != null) {
            vo.setDelaySeconds(delaySeconds);
        }
        vo.setSubscriptionMode(subscriptionMode);
        vo.setConsumeType(consumeType);
        return vo;
    }

    public ConsumerGroupVO mergeWith(ConsumerGroupVO current) {
        ConsumerGroupVO merged = new ConsumerGroupVO();
        org.springframework.beans.BeanUtils.copyProperties(current, merged);
        if (namespace != null) merged.setNamespace(namespace);
        if (subscriptionMode != null) merged.setSubscriptionMode(subscriptionMode);
        if (consumeType != null) merged.setConsumeType(consumeType);
        if (subscriptionDataType != null) merged.setSubscriptionDataType(subscriptionDataType);
        if (deliveryOrderType != null) merged.setDeliveryOrderType(deliveryOrderType);
        if (retryMaxTimes != null) merged.setRetryMaxTimes(retryMaxTimes);
        if (delaySeconds != null) merged.setDelaySeconds(delaySeconds);
        return merged;
    }

    public static GroupInput from(ConsumerGroupVO group) {
        return new GroupInput(
                group.getInstanceId(),
                group.getName(),
                group.getNamespace(),
                group.getSubscriptionMode(),
                group.getConsumeType(),
                group.getSubscriptionDataType(),
                group.getDeliveryOrderType(),
                group.getRetryMaxTimes(),
                group.getDelaySeconds());
    }

}
