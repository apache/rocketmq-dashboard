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

package org.apache.rocketmq.studio.instance.acl;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Matches an IP address against a CIDR block or a single IP.
 *
 * <p>Supports the ACL 2.0 IP whitelist semantics where a bare {@code 0.0.0.0} is treated as a
 * wildcard that matches any address (IPv4 or IPv6), instead of being compared as a literal
 * string. This fixes the old behaviour where {@code 0.0.0.0} only matched itself.
 */
public final class IpRangeMatcher {

    private static final String WILDCARD_V4 = "0.0.0.0";
    private static final String WILDCARD_V4_CIDR = "0.0.0.0/0";
    private static final String WILDCARD_V6_CIDR = "::/0";

    private IpRangeMatcher() {
    }

    /** Matches a strict dotted-quad IPv4 literal (each octet 0-255). */
    private static final Pattern IPV4_LITERAL =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");
    private static final Pattern IPV6_LITERAL_CHARACTERS =
            Pattern.compile("^[0-9a-fA-F:.]+$");

    private static boolean isIpv4Literal(String value) {
        return value != null && IPV4_LITERAL.matcher(value).matches();
    }

    /**
     * Returns {@code true} when {@code ip} is within {@code cidrOrIp}.
     *
     * <ul>
     *   <li>{@code 0.0.0.0}, {@code 0.0.0.0/0} or {@code ::/0} match any valid IP literal.</li>
     *   <li>An entry without a {@code /} matches the equivalent IPv4 or IPv6 literal.</li>
     *   <li>IPv4 and IPv6 CIDR entries are matched using their respective prefix widths.</li>
     *   <li>Any unparseable input or address-family mismatch returns {@code false}.</li>
     * </ul>
     *
     * @param ip       the address being checked (IPv4 or IPv6)
     * @param cidrOrIp the whitelist entry to match against
     * @return whether the address is in range
     */
    public static boolean isInRange(String ip, String cidrOrIp) {
        if (ip == null || ip.isBlank() || cidrOrIp == null || cidrOrIp.isBlank()) {
            return false;
        }
        String target = ip.trim();
        String entry = cidrOrIp.trim();

        if (WILDCARD_V4.equals(entry) || WILDCARD_V4_CIDR.equals(entry) || WILDCARD_V6_CIDR.equals(entry)) {
            return parseAddressLiteral(target) != null;
        }

        int slash = entry.indexOf('/');
        if (slash < 0) {
            byte[] targetBytes = parseAddressLiteral(target);
            byte[] entryBytes = parseAddressLiteral(entry);
            return targetBytes != null && Arrays.equals(targetBytes, entryBytes);
        }

        String baseIp = entry.substring(0, slash);
        String prefixStr = entry.substring(slash + 1);
        int prefix;
        try {
            prefix = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] targetBytes = parseAddressLiteral(target);
        byte[] baseBytes = parseAddressLiteral(baseIp);
        if (targetBytes == null || baseBytes == null || targetBytes.length != baseBytes.length
                || prefix < 0 || prefix > baseBytes.length * Byte.SIZE) {
            return false;
        }
        int bits = prefix;
        for (int i = 0; i < targetBytes.length && bits > 0; i++) {
            int mask = (bits >= 8) ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
            if ((targetBytes[i] & mask) != (baseBytes[i] & mask)) {
                return false;
            }
            bits -= 8;
        }
        return true;
    }

    /**
     * Parses an IPv4 or IPv6 literal without resolving hostnames. IPv6 input is restricted to
     * address-literal characters before using {@link InetAddress#getByName(String)}, so this path
     * cannot issue a DNS query. Scoped addresses are intentionally rejected because interface
     * names are host-specific and cannot form portable ACL entries.
     */
    private static byte[] parseAddressLiteral(String value) {
        if (isIpv4Literal(value)) {
            String[] parts = value.split("\\.");
            byte[] bytes = new byte[4];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(parts[i]);
            }
            return bytes;
        }
        if (value == null || !value.contains(":")
                || !IPV6_LITERAL_CHARACTERS.matcher(value).matches()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address ? address.getAddress() : null;
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    /**
     * Returns {@code true} when {@code cidrOrIp} is a well-formed whitelist entry: a wildcard
     * ({@code 0.0.0.0}, {@code 0.0.0.0/0}, {@code ::/0}), a bare IPv4/IPv6 address, or a CIDR with a
     * prefix valid for that address family. Used to validate ACL 2.0 {@code whiteSet} entries before
     * they are applied.
     */
    public static boolean isValidRange(String cidrOrIp) {
        if (cidrOrIp == null || cidrOrIp.isBlank()) {
            return false;
        }
        String entry = cidrOrIp.trim();
        if (WILDCARD_V4.equals(entry) || WILDCARD_V4_CIDR.equals(entry) || WILDCARD_V6_CIDR.equals(entry)) {
            return true;
        }
        int slash = entry.indexOf('/');
        if (slash < 0) {
            return parseAddressLiteral(entry) != null;
        }
        String baseIp = entry.substring(0, slash);
        String prefixStr = entry.substring(slash + 1);
        int prefix;
        try {
            prefix = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] baseBytes = parseAddressLiteral(baseIp);
        return baseBytes != null && prefix >= 0 && prefix <= baseBytes.length * Byte.SIZE;
    }
}
