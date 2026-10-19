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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ProxyHomeVO}, the state of the proxy home selector: the address list to pick
 * from and the currently selected address. The selector only works because "current" is one
 * of the listed addresses — an empty list with a stray current selection would render a
 * broken picker.
 */
class ProxyHomeVOTest {

    @Test
    void theHomeCarriesTheAddressListAndTheCurrentSelection() {
        ProxyHomeVO home = ProxyHomeVO.builder()
                .proxyAddrList(List.of("10.0.0.1:8080", "10.0.0.2:8080"))
                .currentProxyAddr("10.0.0.1:8080")
                .build();

        assertThat(home.getProxyAddrList()).containsExactly("10.0.0.1:8080", "10.0.0.2:8080");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("10.0.0.1:8080");
        assertThat(home.getProxyAddrList()).contains(home.getCurrentProxyAddr());
    }

    @Test
    void anEmptyHomeIsExpressibleAndDistinct() {
        ProxyHomeVO empty = ProxyHomeVO.builder().build();

        assertThat(empty.getProxyAddrList()).isNull();
        assertThat(empty.getCurrentProxyAddr()).isNull();
        assertThat(empty).isEqualTo(ProxyHomeVO.builder().build());
        assertThat(empty).isNotEqualTo(ProxyHomeVO.builder()
                .proxyAddrList(List.of("10.0.0.1:8080"))
                .currentProxyAddr("10.0.0.1:8080")
                .build());
    }
}
