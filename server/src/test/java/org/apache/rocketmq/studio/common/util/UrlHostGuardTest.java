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

class UrlHostGuardTest {

    @Test
    void rejectsLoopbackWhenNotAllowed() {
        assertThat(UrlHostGuard.isAllowedHost("127.0.0.1", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("localhost", false)).isFalse();
    }

    @Test
    void allowsLoopbackWhenExplicitlyEnabled() {
        assertThat(UrlHostGuard.isAllowedHost("127.0.0.1", true)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("localhost", true)).isTrue();
    }

    @Test
    void rejectsLinkLocalAndAnyLocalAddresses() {
        assertThat(UrlHostGuard.isAllowedHost("169.254.169.254", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("0.0.0.0", false)).isFalse();
    }

    @Test
    void allowsPublicAndPrivateEndpointAddresses() {
        assertThat(UrlHostGuard.isAllowedHost("10.0.0.1", false)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("192.168.1.10", false)).isTrue();
        assertThat(UrlHostGuard.isAllowedHost("8.8.8.8", false)).isTrue();
    }

    @Test
    void rejectsBlankAndUnresolvableHosts() {
        assertThat(UrlHostGuard.isAllowedHost(null, false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("  ", false)).isFalse();
        assertThat(UrlHostGuard.isAllowedHost("definitely-not-a-real-host.invalid", false)).isFalse();
    }
}
