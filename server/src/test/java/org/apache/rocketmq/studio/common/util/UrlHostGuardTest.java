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

class UrlHostGuardTest {

    @Test
    void shouldAllowOnlyWhenEveryAddressPassesThePolicy() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("192.0.2.1");
        InetAddress privateAddress = InetAddress.getByName("10.1.2.3");

        assertThat(UrlHostGuard.areAllowedAddresses(
                new InetAddress[] {publicAddress, privateAddress}, false)).isTrue();
    }

    @Test
    void shouldRejectMixedSafeAndLinkLocalAddresses() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("192.0.2.1");
        InetAddress metadataAddress = InetAddress.getByName("169.254.169.254");

        assertThat(UrlHostGuard.areAllowedAddresses(
                new InetAddress[] {publicAddress, metadataAddress}, false)).isFalse();
        assertThat(UrlHostGuard.areAllowedAddresses(
                new InetAddress[] {metadataAddress, publicAddress}, false)).isFalse();
    }

    @Test
    void shouldApplyLoopbackPolicyToEveryAddress() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("192.0.2.1");
        InetAddress loopbackAddress = InetAddress.getByName("127.0.0.1");
        InetAddress[] mixedAddresses = {publicAddress, loopbackAddress};

        assertThat(UrlHostGuard.areAllowedAddresses(mixedAddresses, false)).isFalse();
        assertThat(UrlHostGuard.areAllowedAddresses(mixedAddresses, true)).isTrue();
    }

    @Test
    void shouldRejectMissingOrInvalidAddressSets() throws Exception {
        assertThat(UrlHostGuard.areAllowedAddresses(null, false)).isFalse();
        assertThat(UrlHostGuard.areAllowedAddresses(new InetAddress[0], false)).isFalse();
        assertThat(UrlHostGuard.areAllowedAddresses(
                new InetAddress[] {InetAddress.getByName("192.0.2.1"), null}, false)).isFalse();
        assertThat(UrlHostGuard.areAllowedAddresses(
                new InetAddress[] {InetAddress.getByName("0.0.0.0")}, true)).isFalse();
    }
}
