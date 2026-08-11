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
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

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

    private InstanceVO proxyInstance(String id, String endpoint) {
        InstanceVO instance = InstanceVO.builder().type(InstanceType.PROXY).endpoint(endpoint).build();
        instance.setId(id);
        return instance;
    }
}
