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

package org.apache.rocketmq.studio.cluster.proxy;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyAddressServiceTest {

    private final ProxyAddressService proxyAddressService = new ProxyAddressService();

    @Test
    void homePageShouldReturnDefaultProxyAddress() {
        ProxyHomeVO home = proxyAddressService.getHomePage();

        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("127.0.0.1:8081");
    }

    @Test
    void addProxyAddrShouldTrimAndKeepUniqueAddresses() {
        proxyAddressService.addProxyAddr(" 10.0.0.1:8081 ");
        proxyAddressService.addProxyAddr("10.0.0.1:8081");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081", "10.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("127.0.0.1:8081");
    }

    @Test
    void addProxyAddrShouldRejectBlankAddress() {
        assertThatThrownBy(() -> proxyAddressService.addProxyAddr(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("newProxyAddr is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void addProxyAddrShouldAcceptBracketedIpv6Address() {
        proxyAddressService.addProxyAddr(" [::1]:8081 ");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081", "[::1]:8081");
    }

    @Test
    void addProxyAddrShouldRejectInvalidAddressFormats() {
        List<String> invalidProxyAddrs = List.of(
                "10.0.0.1",
                "10.0.0.1:abc",
                "10.0.0.1:0",
                "10.0.0.1:65536",
                "http://10.0.0.1:8081",
                "10.0.0.1:8081/path"
        );

        for (String invalidProxyAddr : invalidProxyAddrs) {
            assertThatThrownBy(() -> proxyAddressService.addProxyAddr(invalidProxyAddr))
                    .as("invalid proxy address %s", invalidProxyAddr)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
    }

    @Test
    void removeProxyAddrShouldTrimAndRemoveAddress() {
        proxyAddressService.addProxyAddr("10.0.0.1:8081");

        proxyAddressService.removeProxyAddr(" 10.0.0.1:8081 ");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("127.0.0.1:8081");
    }

    @Test
    void removeProxyAddrShouldSelectNextProxyWhenCurrentIsRemoved() {
        proxyAddressService.removeProxyAddr("127.0.0.1:8081");

        ProxyHomeVO emptyHome = proxyAddressService.getHomePage();
        assertThat(emptyHome.getProxyAddrList()).isEmpty();
        assertThat(emptyHome.getCurrentProxyAddr()).isEmpty();

        proxyAddressService.addProxyAddr("10.0.0.1:8081");
        proxyAddressService.addProxyAddr("10.0.0.2:8081");
        proxyAddressService.removeProxyAddr("10.0.0.1:8081");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("10.0.0.2:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("10.0.0.2:8081");
    }

    @Test
    void removeProxyAddrShouldRejectBlankAddress() {
        assertThatThrownBy(() -> proxyAddressService.removeProxyAddr(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("proxyAddr is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void removeProxyAddrShouldRejectUnknownAddress() {
        assertThatThrownBy(() -> proxyAddressService.removeProxyAddr("10.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy address not found: 10.0.0.1:8081")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }
}
