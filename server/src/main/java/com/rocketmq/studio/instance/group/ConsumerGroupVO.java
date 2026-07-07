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
package com.rocketmq.studio.instance.group;

import com.rocketmq.studio.common.domain.BaseEntity;
import com.rocketmq.studio.common.domain.enums.ConsumeType;
import com.rocketmq.studio.common.domain.enums.SubscriptionMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConsumerGroupVO extends BaseEntity {
    private String name;
    private String namespace;
    private String clusterId;
    private SubscriptionMode subscriptionMode;
    private ConsumeType consumeType;
    private int onlineInstances;
    private long totalLag;
    private List<String> subscribedTopics;
    private String subscriptionDataType;
    private String deliveryOrderType;
    private int retryMaxTimes;
    private int delaySeconds;
    private List<ConsumerInstanceVO> instances;
}
