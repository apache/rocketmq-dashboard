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
}
