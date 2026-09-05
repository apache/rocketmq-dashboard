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
package org.apache.rocketmq.studio.provider.tencent;

import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TencentCatalogController}: the Tencent cloud-catalog endpoints
 * delegate to the catalog service with the credential/region/search parameters intact.
 */
@ExtendWith(MockitoExtension.class)
class TencentCatalogControllerTest {

    @Mock
    private TencentCatalogService catalogService;

    @InjectMocks
    private TencentCatalogController controller;

    @Test
    void listsRegionsForACredential() {
        List<CloudRegionVO> regions = List.of();
        when(catalogService.listRegions(12L)).thenReturn(regions);

        assertThat(controller.listRegions(12L).getData()).isSameAs(regions);
        verify(catalogService).listRegions(12L);
    }

    @Test
    void listsInstancesWithRegionAndSearch() {
        List<CloudInstanceOptionVO> instances = List.of();
        when(catalogService.listCloudInstances(12L, "ap-guangzhou", "prod")).thenReturn(instances);

        assertThat(controller.listInstances(12L, "ap-guangzhou", "prod").getData())
                .isSameAs(instances);
        verify(catalogService).listCloudInstances(12L, "ap-guangzhou", "prod");
    }

    @Test
    void passesNullSearchThroughWhenAbsent() {
        when(catalogService.listCloudInstances(12L, "ap-guangzhou", null))
                .thenReturn(List.of());

        assertThat(controller.listInstances(12L, "ap-guangzhou", null).getData()).isEmpty();
        verify(catalogService).listCloudInstances(12L, "ap-guangzhou", null);
    }
}
