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

package com.rocketmq.studio.cluster.proxy;

import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

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
}
