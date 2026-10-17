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

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the Tencent catalog endpoints: each delegates to the catalog service with the request
 * parameters verbatim (the optional search passes through as null when absent) and wraps the
 * answer in a success {@link Result}.
 */
class TencentCatalogControllerTest {

    private final TencentCatalogService catalogService = mock(TencentCatalogService.class);
    private final TencentCatalogController controller = new TencentCatalogController(catalogService);

    @Test
    void listRegionsDelegatesAndWrapsTheAnswer() {
        CloudRegionVO region = new CloudRegionVO();
        region.setRegionId("ap-guangzhou");
        region.setRegionName("Guangzhou");
        when(catalogService.listRegions(7L)).thenReturn(List.of(region));

        Result<List<CloudRegionVO>> result = controller.listRegions(7L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).containsExactly(region);
    }

    @Test
    void listInstancesPassesEveryParameterThrough() {
        CloudInstanceOptionVO instance = new CloudInstanceOptionVO();
        instance.setInstanceId("ins-1");
        when(catalogService.listCloudInstances(7L, "ap-guangzhou", "order")).thenReturn(List.of(instance));

        Result<List<CloudInstanceOptionVO>> result = controller.listInstances(7L, "ap-guangzhou", "order");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(instance);
    }

    @Test
    void anAbsentSearchIsForwardedAsNull() {
        when(catalogService.listCloudInstances(7L, "ap-guangzhou", null)).thenReturn(List.of());

        Result<List<CloudInstanceOptionVO>> result = controller.listInstances(7L, "ap-guangzhou", null);

        assertThat(result.getData()).isEmpty();
    }
}
