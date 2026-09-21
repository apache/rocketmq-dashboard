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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
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
 *
 * <p>A broker configured with a hostname in {@code brokerIP1} is published to the name server
 * under that hostname, while an offset message id always decodes to a numeric address, so the two
 * sides are compared as resolved addresses instead of as strings. The accepted set therefore widens
 * from "the literal registered endpoint" to "whatever the registered host currently resolves to":
 * an id is admitted when the address it embeds equals the address that a registered endpoint on the
 * same port resolves to at evaluation time. A registered host that does not resolve matches nothing,
 * so an id pointing at it stays rejected.
 *
 * <p>Resolution means DNS, so the expected cost is stated here. The literal comparison runs first and
 * is free, which is all a cluster with numeric {@code brokerIP1} values ever pays. When it does not
 * match, one guard evaluation makes at most one {@link BrokerHostResolver} call for the embedded host
 * — always a numeric literal, which the platform resolver parses locally — plus at most one per
 * registered endpoint carrying the same port; only registered hostnames reach DNS. Those are calls,
 * not lookups: a caller that shares one {@link BrokerHostResolver#caching(BrokerHostResolver)}
 * instance across a request resolves each distinct host once however often the guard runs, and a host
 * that fails to resolve is not retried either. The resolver is {@link BrokerHostResolver#DEFAULT}
 * unless a caller supplies another one, and the platform lookup it wraps has no wall-clock bound of
 * its own — see {@link BrokerHostResolver#DEFAULT}.
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
     *
     * @param hostResolver resolves registered endpoints that are hostnames; pass a shared
     *                     {@link BrokerHostResolver#caching(BrokerHostResolver)} instance when the
     *                     guard is evaluated more than once while serving one request
     */
    public static boolean isWithinKnownBrokerTopology(MQAdminExt admin, String msgId,
                                                      BrokerHostResolver hostResolver) {
        MessageId messageId;
        try {
            messageId = MessageDecoder.decodeMessageId(msgId);
        } catch (Exception e) {
            return true;
        }
        try {
            return validatedBrokerAddr(admin, msgId, messageId, hostResolver) != null;
        } catch (Exception e) {
            log.warn("Could not verify broker topology for msgId={}: {}", msgId, e.getMessage());
            return false;
        }
    }

    /** Returns the embedded broker address when it is a known endpoint, otherwise null. */
    public static String validatedBrokerAddr(MQAdminExt admin, String msgId, MessageId messageId,
                                             BrokerHostResolver hostResolver) throws Exception {
        String brokerAddr = decodedBrokerAddr(messageId);
        if (!StringUtils.hasText(brokerAddr)) {
            return null;
        }
        if (matchesKnownEndpoint(admin, brokerAddr, hostResolver)) {
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

    /**
     * Whether {@code brokerAddr} is one of the endpoints the selected instance publishes.
     *
     * <p>The literal comparison runs first and costs nothing, which is the only path a cluster with
     * numeric {@code brokerIP1} values ever takes. Otherwise the embedded address and every
     * registered endpoint that carries the same port are resolved through {@code hostResolver},
     * stopping at the first match — at most one call for the embedded host plus at most one per
     * same-port endpoint. Only registered hosts that are hostnames reach DNS: the embedded address is
     * a numeric literal, which the platform resolver parses locally, and a resolver that has already
     * answered — {@link BrokerHostResolver#caching(BrokerHostResolver)} — answers again without a
     * lookup. Brokers whose registered host does not resolve match nothing, which keeps the failure
     * direction closed.
     *
     * @param hostResolver the resolver to look hosts up with; see
     *                     {@link #isWithinKnownBrokerTopology(MQAdminExt, String, BrokerHostResolver)}
     *                     on sharing one instance per request
     */
    static boolean matchesKnownEndpoint(MQAdminExt admin, String brokerAddr,
                                        BrokerHostResolver hostResolver) throws Exception {
        Set<String> endpoints = knownBrokerEndpoints(admin);
        if (endpoints.contains(brokerAddr)) {
            return true;
        }
        int separator = brokerAddr.lastIndexOf(':');
        if (separator <= 0 || separator == brokerAddr.length() - 1) {
            return false;
        }
        String decodedPort = brokerAddr.substring(separator + 1);
        InetAddress decodedAddress = resolveHost(hostResolver, brokerAddr.substring(0, separator));
        if (decodedAddress == null) {
            return false;
        }
        for (String endpoint : endpoints) {
            int endpointSeparator = endpoint.lastIndexOf(':');
            if (endpointSeparator <= 0
                    || !decodedPort.equals(endpoint.substring(endpointSeparator + 1))) {
                continue;
            }
            if (decodedAddress.equals(resolveHost(hostResolver, endpoint.substring(0, endpointSeparator)))) {
                return true;
            }
        }
        return false;
    }

    private static InetAddress resolveHost(BrokerHostResolver hostResolver, String host) {
        try {
            return hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            return null;
        }
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
