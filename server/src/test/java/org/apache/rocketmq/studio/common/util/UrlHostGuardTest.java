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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlHostGuardTest {

    @Test
    void areAllowedShouldRejectAHostWhenAnyResolvedAddressIsDisallowed() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        InetAddress loopbackAddress = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});

        assertThat(UrlHostGuard.areAllowed(
                new InetAddress[]{publicAddress, loopbackAddress}, false)).isFalse();
    }

    @Test
    void areAllowedShouldAcceptEveryPublicResolvedAddress() throws Exception {
        InetAddress first = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        InetAddress second = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});

        assertThat(UrlHostGuard.areAllowed(new InetAddress[]{first, second}, false)).isTrue();
    }

    @Test
    void areAllowedShouldRejectIpv6UniqueLocalAddresses() throws Exception {
        // fc00::/7 unique-local addresses are not classified as link/site-local by the JDK
        // and would otherwise pass the guard; fd00:ec2::254 is the AWS EC2 IMDS IPv6 endpoint.
        InetAddress ula = InetAddress.getByName("fd00:ec2::254");
        InetAddress ulaFc = InetAddress.getByName("fc00::1");

        assertThat(UrlHostGuard.areAllowed(new InetAddress[]{ula}, false)).isFalse();
        assertThat(UrlHostGuard.areAllowed(new InetAddress[]{ulaFc}, false)).isFalse();
    }

    @Test
    void areAllowedShouldStillAcceptGlobalIpv6Addresses() throws Exception {
        InetAddress globalIpv6 = InetAddress.getByName("2606:4700:4700::1111");

        assertThat(UrlHostGuard.areAllowed(new InetAddress[]{globalIpv6}, false)).isTrue();
    }

    @Test
    void isAllowedHostShouldRejectIpv6UlaLiteralEvenWhenLoopbackIsAllowed() {
        assertThat(UrlHostGuard.isAllowedHost("fd00:ec2::254", true)).isFalse();
    }

    @Test
    void checkShouldAcceptHttpHttpsAndRejectOtherSchemes() {
        UrlHostGuard.check("http://93.184.216.34/metrics", false);
        UrlHostGuard.check("https://93.184.216.34/metrics", false);

        assertThatThrownBy(() -> UrlHostGuard.check("ftp://93.184.216.34/metrics", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with http");
    }

    @Test
    void checkShouldRejectMissingBlankAndHostlessUrls() {
        assertThatThrownBy(() -> UrlHostGuard.check(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL is required");
        assertThatThrownBy(() -> UrlHostGuard.check("   ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL is required");
        assertThatThrownBy(() -> UrlHostGuard.check("http://", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URL is not a valid URI");
    }

    @Test
    void checkShouldGateLoopbackAndMetadataAddresses() {
        assertThatThrownBy(() -> UrlHostGuard.check("http://127.0.0.1:11434", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlHostGuard.check("http://169.254.169.254/latest", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");

        UrlHostGuard.check("http://127.0.0.1:11434", true);
        UrlHostGuard.check("https://93.184.216.34///", false);
    }
}
