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
package com.rocketmq.studio.cluster.broker;

import com.rocketmq.studio.cluster.config.ClusterConfigVO;
import com.rocketmq.studio.cluster.nameserver.NameServerVO;
import com.rocketmq.studio.cluster.proxy.ProxyVO;

import com.rocketmq.studio.common.domain.BaseEntity;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClusterVO extends BaseEntity {
    private String name;
    private String nsClusterName;
    private ClusterType type;
    private String endpoint;
    private ClusterStatus status;
    private String version;
    private List<BrokerVO> brokers;
    private List<ProxyVO> proxies;
    private List<NameServerVO> nameServers;
    private ClusterConfigVO config;
    private int topicCount;
    private int groupCount;
    private List<Integer> tpsHistory;
}
