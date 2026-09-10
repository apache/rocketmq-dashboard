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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlHostGuardMulticastTest {

    @Test
    void isAllowedHostShouldRejectIpv4AndIpv6MulticastAddresses() {
        assertThat(UrlHostGuard.isAllowedHost("224.0.0.1", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("ff02::1", false)).isFalse();
    }

    @Test
    void isAllowedHostShouldRejectBlankHostsFailClosed() {
        assertThat(UrlHostGuard.isAllowedHost(null, false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("   ", false)).isFalse();
    }

    @Test
    void isAllowedHostShouldRejectAnyLocalAndMetadataAddresses() {
        assertThat(UrlHostGuard.isAllowedHost("0.0.0.0", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("::", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("169.254.169.254", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("fd00:ec2::254", false)).isFalse();
    }

    @Test
    void isAllowedHostShouldGateLoopbackOnTheFlag() {
        assertThat(UrlHostGuard.isAllowedHost("localhost", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("localhost", true)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("127.0.0.1", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("127.0.0.1", true)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("::1", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("::1", true)).isTrue();
    }

    @Test
    void isAllowedHostShouldStripATrailingDotBeforeClassification() {
        assertThat(UrlHostGuard.isAllowedHost("127.0.0.1.", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("localhost.", true)).isTrue();
    }

    @Test
    void isAllowedHostShouldKeepPrivateSiteLocalRangesAllowed() {
        assertThat(UrlHostGuard.isAllowedHost("10.0.0.5", false)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("172.16.0.8", false)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("192.168.1.10", false)).isTrue();
    }

    @Test
    void checkShouldRejectMissingAndBlankUrls() {
        assertThatThrownBy(() -> UrlHostGuard.check(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is required");
        assertThatThrownBy(() -> UrlHostGuard.check("   ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL is required");
    }

    @Test
    void checkShouldRejectNonHttpSchemes() {
        assertThatThrownBy(() -> UrlHostGuard.check("ftp://10.0.0.5/file", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// or https://");
        assertThatThrownBy(() -> UrlHostGuard.check("file:///etc/passwd", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// or https://");
    }

    @Test
    void checkShouldRequireAHost() {
        assertThatThrownBy(() -> UrlHostGuard.check("http:///path", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must include a host");
    }

    @Test
    void checkShouldRejectMetadataAndLoopbackWithoutTheFlag() {
        assertThatThrownBy(
                () -> UrlHostGuard.check("http://169.254.169.254/latest/meta-data/", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local, loopback or metadata");
        assertThatThrownBy(() -> UrlHostGuard.check("http://localhost:11434", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local, loopback or metadata");
    }

    @Test
    void checkShouldAdmitLoopbackWhenAllowed() {
        UrlHostGuard.check("http://localhost:11434/v1/chat/completions", true);
        UrlHostGuard.check("http://127.0.0.1:11434", true);
    }

    @Test
    void checkShouldAcceptTrimmedHttpUrlsToPrivateHosts() {
        UrlHostGuard.check("  https://10.0.0.5:9090/prometheus  ", false);
        UrlHostGuard.check("http://192.168.1.10//", false);
    }
}
