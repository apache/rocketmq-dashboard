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

package org.apache.rocketmq.dashboard.service.strategy;

import lombok.AllArgsConstructor;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AclUserStrategy implements UserStrategy {

    private static final Logger log = LoggerFactory.getLogger(AclUserStrategy.class);

    private final MQAdminExt mqAdminExt;

    private final ClusterInfoService clusterInfoService;

    @Override
    public UserInfo getUserInfoByUsername(String username) {
        ClusterInfo clusterInfo = clusterInfoService.get();

        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null || clusterInfo.getBrokerAddrTable().isEmpty()) {
            log.warn("Cluster information is not available or has no broker addresses.");
            return null;
        }
        for (BrokerData brokerLiveInfo : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerLiveInfo == null || brokerLiveInfo.getBrokerAddrs() == null || brokerLiveInfo.getBrokerAddrs().isEmpty()) {
                continue;
            }
            String brokerAddr = brokerLiveInfo.getBrokerAddrs().get(0L); // Assuming 0L is the primary address
            if (brokerAddr == null) {
                continue;
            }
            try {
                UserInfo userInfo = mqAdminExt.getUser(brokerAddr, username);
                if (userInfo != null) {
                    return userInfo;
                }
            } catch (Exception e) {
                log.warn("Failed to get user {} from broker {}. Trying next broker if available. Error: {}", username, brokerAddr, e.getMessage());
            }
        }
        return null;
    }
}
