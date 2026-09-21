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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Test double for {@link BrokerHostResolver}: registered hostnames are answered from an in-memory map
 * and never from DNS, a numeric literal is parsed locally exactly as the platform resolver does, and
 * any other host is unresolvable. Every lookup is recorded, so a test can assert both that the guard
 * resolved the endpoints at all and how many times it did.
 */
final class FakeBrokerHostResolver implements BrokerHostResolver {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final Map<String, InetAddress> hostnames = new LinkedHashMap<>();
    private final List<String> lookups = new ArrayList<>();

    /** Registers {@code host} as resolving to the numeric literal {@code address}. */
    FakeBrokerHostResolver registered(String host, String address) throws UnknownHostException {
        hostnames.put(host, InetAddress.getByName(address));
        return this;
    }

    @Override
    public InetAddress resolve(String host) throws UnknownHostException {
        lookups.add(host);
        InetAddress registered = hostnames.get(host);
        if (registered != null) {
            return registered;
        }
        if (isAddressLiteral(host)) {
            return InetAddress.getByName(host);
        }
        throw new UnknownHostException(host);
    }

    /** Every host the guard asked about, in order, repeats included. */
    List<String> lookups() {
        return List.copyOf(lookups);
    }

    /** How many times {@code host} was asked about, repeats included. */
    long lookupCount(String host) {
        return lookups.stream().filter(host::equals).count();
    }

    private static boolean isAddressLiteral(String host) {
        return IPV4_LITERAL.matcher(host).matches() || host.indexOf(':') >= 0;
    }
}
