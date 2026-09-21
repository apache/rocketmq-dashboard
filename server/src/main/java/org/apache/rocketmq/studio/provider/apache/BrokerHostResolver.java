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
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the host part of a broker endpoint published by the name server, so
 * {@link BrokerTopologyGuards} can compare a hostname-registered endpoint with the numeric address
 * embedded in an offset message id.
 *
 * <p>The lookup is DNS, so it is injected instead of hard-wired: production resolves through
 * {@link #DEFAULT} (the platform resolver behind {@link InetAddress#getByName(String)}), a caller
 * that evaluates the guard more than once while serving one request shares a single
 * {@link #caching(BrokerHostResolver)} instance, and a test supplies a fake so the guard does not
 * touch the network.
 */
@FunctionalInterface
public interface BrokerHostResolver {

    /**
     * The platform lookup, {@link InetAddress#getByName(String)}; production resolves through this one.
     *
     * <p>It carries no wall-clock bound of its own — {@link InetAddress} exposes no timeout knob, so
     * the JVM and the operating system resolver decide how long a hanging lookup blocks. That is part
     * of why the lookup sits behind this interface: a deployment that needs a hard bound, a
     * cross-request cache or an alternate resolver can supply it without touching the guard. The
     * number of lookups is bounded independently, see {@link #caching(BrokerHostResolver)}; numeric
     * literals, which is what an offset message id always decodes to, never reach a network lookup.
     */
    BrokerHostResolver DEFAULT = InetAddress::getByName;

    /**
     * Resolves {@code host} — a hostname or a numeric literal — to the address it currently maps to.
     *
     * @param host the host part of a broker endpoint, without the port
     * @return the address {@code host} resolves to
     * @throws UnknownHostException when {@code host} does not resolve; the guard treats that as
     *                              "not a known endpoint" and rejects the message id
     */
    InetAddress resolve(String host) throws UnknownHostException;

    /**
     * Wraps {@code delegate} into a resolver that looks up every distinct host at most once, and
     * remembers failures as well as successes, so a registered hostname that does not resolve is not
     * retried within the same instance. The returned resolver holds the results in memory, so it is
     * meant to live for a single request: that is what turns a repeated guard evaluation into a
     * repeated comparison instead of repeated DNS, without caching a topology decision across
     * requests.
     *
     * @param delegate the resolver that performs the actual lookup
     * @return a memoizing resolver
     */
    static BrokerHostResolver caching(BrokerHostResolver delegate) {
        Objects.requireNonNull(delegate, "delegate");
        Map<String, Optional<InetAddress>> resolved = new ConcurrentHashMap<>();
        return host -> resolved.computeIfAbsent(host, key -> {
            try {
                return Optional.ofNullable(delegate.resolve(key));
            } catch (UnknownHostException exception) {
                return Optional.empty();
            }
        }).orElseThrow(() -> new UnknownHostException(host));
    }
}
