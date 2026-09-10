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
package org.apache.rocketmq.studio.provider;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceProviderRegistryTest {

    @Mock
    private InstanceRepository instanceRepository;

    private InstanceProviderRegistry registry;
    private InstanceProvider apacheProvider;
    private InstanceProvider aliyunProvider;

    @BeforeEach
    void setUp() {
        apacheProvider = stubProvider(InstanceVendor.APACHE);
        aliyunProvider = stubProvider(InstanceVendor.ALIYUN);
        registry = new InstanceProviderRegistry(List.of(apacheProvider, aliyunProvider), List.of(), instanceRepository);
    }

    @Test
    void forVendorShouldReturnRegisteredProviderTest() {
        assertThat(registry.forVendor(InstanceVendor.ALIYUN)).isSameAs(aliyunProvider);
    }

    @Test
    void forVendorShouldThrowWhenMissingTest() {
        assertThatThrownBy(() -> registry.forVendor(InstanceVendor.TENCENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }

    @Test
    void byInstanceIdShouldReturnEmptyForBlankIdTest() {
        assertThat(registry.byInstanceId(null)).isEmpty();
        assertThat(registry.byInstanceId("  ")).isEmpty();
    }

    @Test
    void byInstanceIdShouldThrowWhenInstanceMissingTest() {
        when(instanceRepository.findByIdentifier("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registry.byInstanceId("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void byInstanceIdShouldResolveVendorProviderTest() {
        InstanceVO instance = InstanceVO.builder().vendor(InstanceVendor.ALIYUN).build();
        when(instanceRepository.findByIdentifier("inst-1")).thenReturn(Optional.of(instance));

        assertThat(registry.byInstanceId("inst-1")).containsSame(aliyunProvider);
    }

    @Test
    void byInstanceIdShouldDefaultToApacheWhenVendorNullTest() {
        InstanceVO instance = InstanceVO.builder().build();
        when(instanceRepository.findByIdentifier("inst-2")).thenReturn(Optional.of(instance));

        assertThat(registry.byInstanceId("inst-2")).containsSame(apacheProvider);
    }

    @Test
    void catalogForShouldThrowWhenNoCatalogRegisteredTest() {
        assertThatThrownBy(() -> registry.catalogFor(InstanceVendor.ALIYUN))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }

    @Test
    void constructorShouldRejectDuplicateProvidersForVendorTest() {
        InstanceProvider duplicate = stubProvider(InstanceVendor.APACHE);

        assertThatThrownBy(() -> new InstanceProviderRegistry(
                List.of(apacheProvider, duplicate), List.of(), instanceRepository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate instance provider registered for vendor APACHE");
    }

    @Test
    void constructorShouldRejectDuplicateCatalogsForVendorTest() {
        CloudCatalogProvider first = stubCatalog(InstanceVendor.ALIYUN);
        CloudCatalogProvider duplicate = stubCatalog(InstanceVendor.ALIYUN);

        assertThatThrownBy(() -> new InstanceProviderRegistry(
                List.of(apacheProvider), List.of(first, duplicate), instanceRepository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate cloud catalog provider registered for vendor ALIYUN");
    }

    @Test
    void catalogForShouldReturnRegisteredCatalogTest() {
        CloudCatalogProvider catalog = stubCatalog(InstanceVendor.ALIYUN);
        registry = new InstanceProviderRegistry(List.of(apacheProvider), List.of(catalog), instanceRepository);

        assertThat(registry.catalogFor(InstanceVendor.ALIYUN)).isSameAs(catalog);
    }

    @Test
    void forVendorShouldReportNullVendorAsMissingTest() {
        // EnumMap.get(null) NPEs; a null vendor must surface as 501 like an unknown vendor.
        assertThatThrownBy(() -> registry.forVendor(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No instance provider registered for vendor null")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }

    @Test
    void catalogForShouldReportNullVendorAsMissingTest() {
        assertThatThrownBy(() -> registry.catalogFor(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No cloud catalog provider registered for vendor null")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }

    private InstanceProvider stubProvider(InstanceVendor vendor) {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.vendor()).thenReturn(vendor);
        return provider;
    }

    private CloudCatalogProvider stubCatalog(InstanceVendor vendor) {
        CloudCatalogProvider catalog = mock(CloudCatalogProvider.class);
        when(catalog.vendor()).thenReturn(vendor);
        return catalog;
    }
}
