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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpRangeMatcherTest {

    @Test
    void bareZeroMatchesAnyIp() {
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "0.0.0.0")).isTrue();
        assertThat(IpRangeMatcher.isInRange("10.0.0.5", "0.0.0.0")).isTrue();
        assertThat(IpRangeMatcher.isInRange("1.2.3.4", "0.0.0.0")).isTrue();
    }

    @Test
    void zeroCidrMatchesAnyIp() {
        assertThat(IpRangeMatcher.isInRange("203.0.113.7", "0.0.0.0/0")).isTrue();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "0.0.0.0/0")).isTrue();
    }

    @Test
    void ipv6WildcardMatchesAnyIp() {
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "::/0")).isTrue();
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "::/0")).isTrue();
    }

    @Test
    void cidrRangeMatchesInSubnet() {
        assertThat(IpRangeMatcher.isInRange("192.168.1.0", "192.168.1.0/24")).isTrue();
        assertThat(IpRangeMatcher.isInRange("192.168.1.42", "192.168.1.0/24")).isTrue();
        assertThat(IpRangeMatcher.isInRange("192.168.1.255", "192.168.1.0/24")).isTrue();
        assertThat(IpRangeMatcher.isInRange("10.0.0.1", "192.168.1.0/24")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.2.1", "192.168.1.0/24")).isFalse();
    }

    @Test
    void exactMatchWithoutSlash() {
        assertThat(IpRangeMatcher.isInRange("10.0.0.1", "10.0.0.1")).isTrue();
        assertThat(IpRangeMatcher.isInRange("10.0.0.1", "10.0.0.2")).isFalse();
    }

    @Test
    void singleHostCidr() {
        assertThat(IpRangeMatcher.isInRange("10.0.0.1", "10.0.0.1/32")).isTrue();
        assertThat(IpRangeMatcher.isInRange("10.0.0.2", "10.0.0.1/32")).isFalse();
    }

    @Test
    void malformedInputReturnsFalse() {
        assertThat(IpRangeMatcher.isInRange("not-an-ip", "192.168.1.0/24")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "not-an-ip/24")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "192.168.1.0/99")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "192.168.1.0/")).isFalse();
        assertThat(IpRangeMatcher.isInRange(null, "192.168.1.0/24")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", null)).isFalse();
        assertThat(IpRangeMatcher.isInRange("   ", "192.168.1.0/24")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "   ")).isFalse();
    }

    @Test
    void ipv6CidrMatchesEquivalentAddressesInSubnet() {
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "2001:db8::/32")).isTrue();
        assertThat(IpRangeMatcher.isInRange("2001:db8:10::abcd", "2001:db8:10::/64")).isTrue();
        assertThat(IpRangeMatcher.isInRange("2001:db9::1", "2001:db8::/32")).isFalse();
        assertThat(IpRangeMatcher.isInRange("2001:db8:11::1", "2001:db8:10::/64")).isFalse();
    }

    @Test
    void ipv6ExactAndSingleHostCidrUseCanonicalAddressBytes() {
        assertThat(IpRangeMatcher.isInRange(
                "2001:0db8:0000:0000:0000:0000:0000:0001", "2001:db8::1")).isTrue();
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "2001:db8::1/128")).isTrue();
        assertThat(IpRangeMatcher.isInRange("2001:db8::2", "2001:db8::1/128")).isFalse();
    }

    @Test
    void mismatchedAddressFamiliesReturnFalse() {
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "192.168.1.0/24")).isFalse();
        assertThat(IpRangeMatcher.isInRange("192.168.1.10", "2001:db8::/64")).isFalse();
    }

    @Test
    void malformedIpv6AndInvalidPrefixesReturnFalse() {
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "2001:db8::/129")).isFalse();
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "2001:db8::/-1")).isFalse();
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "2001:db8::/abc")).isFalse();
        assertThat(IpRangeMatcher.isInRange("2001:db8::1", "2001:db8::1::2/64")).isFalse();
        assertThat(IpRangeMatcher.isInRange("fe80::1%eth0", "fe80::/10")).isFalse();
    }

    @Test
    void leadingAndTrailingWhitespaceIsTrimmed() {
        assertThat(IpRangeMatcher.isInRange(" 192.168.1.42 ", " 192.168.1.0/24 ")).isTrue();
        assertThat(IpRangeMatcher.isInRange("10.0.0.1", " 10.0.0.1 ")).isTrue();
    }

    @Test
    void isValidRangeAcceptsWildcards() {
        assertThat(IpRangeMatcher.isValidRange("0.0.0.0")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("0.0.0.0/0")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("::/0")).isTrue();
    }

    @Test
    void isValidRangeAcceptsBareIpAndCidr() {
        assertThat(IpRangeMatcher.isValidRange("192.168.1.10")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("192.168.1.0/24")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("10.0.0.1/32")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("2001:db8::1")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("2001:db8::/32")).isTrue();
        assertThat(IpRangeMatcher.isValidRange("2001:db8::1/128")).isTrue();
    }

    @Test
    void isValidRangeRejectsMalformedEntries() {
        assertThat(IpRangeMatcher.isValidRange(null)).isFalse();
        assertThat(IpRangeMatcher.isValidRange("   ")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("not-an-ip")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("192.168.1.0/99")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("192.168.1.0/")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("10.0.0.1/abc")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("256.1.1.1")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("2001:db8::/129")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("2001:db8::1::2")).isFalse();
        assertThat(IpRangeMatcher.isValidRange("fe80::1%eth0")).isFalse();
    }
}
