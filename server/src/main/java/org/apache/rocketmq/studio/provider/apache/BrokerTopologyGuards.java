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
package org.apache.rocketmq.studio.provider.apache;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.util.StringUtils;

/**
 * Shared guards for offset-style message ids, which embed a broker address that
 * {@code MQAdminImpl#viewMessage} connects to directly. Ids whose embedded address is
 * outside the selected instance topology must be rejected before that call.
 */
@Slf4j
public final class BrokerTopologyGuards {

    private BrokerTopologyGuards() {}

    /**
     * Returns true when the msgId either does not decode as an offset id (in which case
     * MQAdminImpl takes the unique-key lookup through the topic route and needs no guard)
     * or its embedded broker address belongs to the selected instance topology. When the
     * topology itself cannot be verified, reject instead of handing an unverified address
     * to remoting.
     */
    public static boolean isWithinKnownBrokerTopology(MQAdminExt admin, String msgId) {
        MessageId messageId;
        try {
            messageId = MessageDecoder.decodeMessageId(msgId);
        } catch (Exception e) {
            return true;
        }
        try {
            return validatedBrokerAddr(admin, msgId, messageId) != null;
        } catch (Exception e) {
            log.warn("Could not verify broker topology for msgId={}: {}", msgId, e.getMessage());
            return false;
        }
    }

    /** Returns the embedded broker address when it is a known endpoint, otherwise null. */
    public static String validatedBrokerAddr(MQAdminExt admin, String msgId, MessageId messageId) throws Exception {
        String brokerAddr = decodedBrokerAddr(messageId);
        if (!StringUtils.hasText(brokerAddr)) {
            return null;
        }
        if (knownBrokerEndpoints(admin).contains(brokerAddr)) {
            return brokerAddr;
        }
        log.warn("Rejecting decoded broker address {} for msgId={} because it is not a known broker endpoint"
                + " for the selected instance", brokerAddr, msgId);
        return null;
    }

    static String decodedBrokerAddr(MessageId messageId) {
        SocketAddress address = messageId.getAddress();
        if (!(address instanceof InetSocketAddress)) {
            return null;
        }
        InetSocketAddress inet = (InetSocketAddress) address;
        if (inet.getAddress() == null) {
            return null;
        }
        return inet.getAddress().getHostAddress() + ":" + inet.getPort();
    }

    static Set<String> knownBrokerEndpoints(MQAdminExt admin) throws Exception {
        ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null
                || clusterInfo.getBrokerAddrTable().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> endpoints = new HashSet<>();
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null || brokerData.getBrokerAddrs() == null
                    || brokerData.getBrokerAddrs().isEmpty()) {
                continue;
            }
            for (String brokerAddr : brokerData.getBrokerAddrs().values()) {
                if (StringUtils.hasText(brokerAddr)) {
                    endpoints.add(brokerAddr.trim());
                }
            }
        }
        return endpoints;
    }
}
