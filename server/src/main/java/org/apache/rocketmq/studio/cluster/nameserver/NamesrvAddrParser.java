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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.common.exception.BusinessException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the NameServer address list stored in the registry. Accepts comma or semicolon
 * separated segments in {@code host:port} or {@code [IPv6]:port} form and returns the
 * normalized value: trimmed segments joined by commas with lowercased hosts.
 */
public final class NamesrvAddrParser {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private NamesrvAddrParser() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessException(400, "namesrvAddr must not be blank");
        }
        List<String> segments = new ArrayList<>();
        for (String part : raw.split("[,;]")) {
            String segment = part.trim();
            if (segment.isEmpty()) {
                throw new BusinessException(400, "namesrvAddr contains an empty address segment");
            }
            segments.add(normalizeSegment(segment));
        }
        return String.join(",", segments);
    }

    private static String normalizeSegment(String segment) {
        int portStart = segment.lastIndexOf(':');
        if (portStart <= 0) {
            throw new BusinessException(400, "namesrvAddr segment is missing host:port: " + segment);
        }
        String host = segment.substring(0, portStart);
        String portText = segment.substring(portStart + 1);
        String normalizedHost;
        if (host.startsWith("[") && host.endsWith("]")) {
            String ipv6 = host.substring(1, host.length() - 1);
            if (!isValidIpv6Literal(ipv6)) {
                throw new BusinessException(400, "namesrvAddr segment has a malformed IPv6 literal: " + segment);
            }
            normalizedHost = "[" + ipv6.toLowerCase(Locale.ROOT) + "]";
        } else {
            if (host.isEmpty()) {
                throw new BusinessException(400, "namesrvAddr segment is missing a host: " + segment);
            }
            if (host.chars().anyMatch(ch -> ch == ':' || Character.isWhitespace(ch))) {
                throw new BusinessException(400, "namesrvAddr segment has an unexpected character: " + segment);
            }
            normalizedHost = host.toLowerCase(Locale.ROOT);
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "namesrvAddr segment has a non-numeric port: " + segment);
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new BusinessException(400, "namesrvAddr port is out of range 1-65535: " + segment);
        }
        return normalizedHost + ":" + port;
    }

    private static boolean isValidIpv6Literal(String ipv6) {
        if (ipv6.isEmpty() || ipv6.chars().filter(ch -> ch == ':').count() < 2) {
            return false;
        }
        // Character-set pre-filter: only literal characters are accepted, so getByName
        // below parses the value as an address literal and cannot issue a DNS lookup.
        for (char ch : ipv6.toCharArray()) {
            boolean valid = ch == ':'
                    || Character.isDigit(ch)
                    || ch >= 'a' && ch <= 'f'
                    || ch >= 'A' && ch <= 'F';
            if (!valid) {
                return false;
            }
        }
        // The charset check alone accepts values with too few groups ("1:2:3"); the
        // literal parse is the full structural validation.
        try {
            return InetAddress.getByName(ipv6) instanceof Inet6Address;
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
