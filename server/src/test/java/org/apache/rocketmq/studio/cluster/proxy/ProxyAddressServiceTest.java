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

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyAddressServiceTest {

    @Mock
    private InstanceRepository instanceRepository;

    private ProxyAddressService proxyAddressService;

    @BeforeEach
    void setUp() {
        proxyAddressService = new ProxyAddressService(instanceRepository);
    }

    @Test
    void homePageShouldReturnConfiguredProxyEndpoints() {
        when(instanceRepository.findByType(InstanceType.PROXY)).thenReturn(List.of(
                proxyInstance("proxy-a", "10.0.0.1:8081"),
                proxyInstance("proxy-b", "10.0.0.2:8081")
        ));

        ProxyHomeVO home = proxyAddressService.getHomePage();

        assertThat(home.getProxyAddrList()).containsExactly("10.0.0.1:8081", "10.0.0.2:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("10.0.0.1:8081");
    }

    @Test
    void homePageShouldReturnSelectedProxyInstanceEndpoint() {
        when(instanceRepository.findById("proxy-a")).thenReturn(Optional.of(proxyInstance("proxy-a", "10.0.0.1:8081")));

        ProxyHomeVO home = proxyAddressService.getHomePage("proxy-a");

        assertThat(home.getProxyAddrList()).containsExactly("10.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("10.0.0.1:8081");
    }

    @Test
    void homePageShouldRequireInstanceId() {
        assertThatThrownBy(() -> proxyAddressService.getHomePage(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("instanceId is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void homePageShouldRejectDirectInstance() {
        InstanceVO direct = proxyInstance("direct-a", "127.0.0.1:9876");
        direct.setType(InstanceType.DIRECT);
        when(instanceRepository.findById("direct-a")).thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> proxyAddressService.getHomePage("direct-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance is not a Proxy instance: direct-a")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void homePageShouldRejectMissingInstance() {
        when(instanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proxyAddressService.getHomePage("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance not found: missing")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void homePageShouldRejectProxyInstanceWithoutEndpoint() {
        when(instanceRepository.findById("proxy-a")).thenReturn(Optional.of(proxyInstance("proxy-a", " ")));

        assertThatThrownBy(() -> proxyAddressService.getHomePage("proxy-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy instance has no configured endpoint: proxy-a")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));
    }

    @Test
    void legacyProxyAddressMutationsShouldBeUnsupported() {
        assertThatThrownBy(() -> proxyAddressService.addProxyAddr("10.0.0.1:8081"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Update the managed instance endpoint instead");
        assertThatThrownBy(() -> proxyAddressService.removeProxyAddr("10.0.0.1:8081"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Update the managed instance endpoint instead");
    }

    @Test
    void reloadConfigShouldRejectAddressOutsideConfiguredProxyInstances() {
        when(instanceRepository.findByType(InstanceType.PROXY)).thenReturn(List.of(proxyInstance("proxy-a", "10.0.0.2:8081")));

        assertThatThrownBy(() -> proxyAddressService.reloadConfig("10.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("addr is not a configured proxy endpoint")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void reloadConfigShouldRejectInvalidAddressFormat() {
        assertThatThrownBy(() -> proxyAddressService.reloadConfig("bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("addr must be in host:port or [ipv6]:port format")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    private InstanceVO proxyInstance(String id, String endpoint) {
        InstanceVO instance = InstanceVO.builder().type(InstanceType.PROXY).endpoint(endpoint).build();
        instance.setId(id);
        return instance;
    }
}
