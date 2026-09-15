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

package org.apache.rocketmq.studio.ops;

import org.apache.rocketmq.studio.cluster.nameserver.NamesrvAddrParser;
import org.apache.rocketmq.studio.common.exception.BusinessException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record OpsConnectionSettings(
        List<String> addresses,
        String currentNamesrv,
        boolean useVIPChannel,
        boolean useTLS) {

    public OpsConnectionSettings {
        List<String> normalizedAddresses = normalizeAddresses(addresses);
        String normalizedCurrent = normalizeOptionalAddress(currentNamesrv);
        if (normalizedAddresses.isEmpty()) {
            if (!normalizedCurrent.isEmpty()) {
                throw new BusinessException(400, "currentNamesrv must be one of the managed NameServer addresses");
            }
        } else if (normalizedCurrent.isEmpty()) {
            normalizedCurrent = normalizedAddresses.getFirst();
        } else if (!normalizedAddresses.contains(normalizedCurrent)) {
            throw new BusinessException(400, "currentNamesrv must be one of the managed NameServer addresses");
        }
        addresses = List.copyOf(normalizedAddresses);
        currentNamesrv = normalizedCurrent;
    }

    public static OpsConnectionSettings empty() {
        return new OpsConnectionSettings(List.of(), "", false, false);
    }

    public static OpsConnectionSettings fromNamesrvAddr(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.trim().isEmpty()) {
            return empty();
        }
        List<String> addresses = Arrays.stream(NamesrvAddrParser.normalize(namesrvAddr).split(","))
                .toList();
        return new OpsConnectionSettings(addresses, addresses.getFirst(), false, false);
    }

    private static List<String> normalizeAddresses(List<String> rawAddresses) {
        if (rawAddresses == null || rawAddresses.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rawAddress : rawAddresses) {
            String address = normalizeSingleAddress(rawAddress);
            if (!normalized.add(address)) {
                throw new BusinessException(400, "namesrvAddr already exists: " + address);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeOptionalAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return "";
        }
        return normalizeSingleAddress(rawAddress);
    }

    static String normalizeSingleAddress(String rawAddress) {
        String normalized = NamesrvAddrParser.normalize(rawAddress);
        if (normalized.contains(",")) {
            throw new BusinessException(400, "namesrvAddr must contain exactly one address");
        }
        return normalized;
    }
}
