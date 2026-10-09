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
package org.apache.rocketmq.studio.provider.alibaba;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the Aliyun catalog endpoints: each delegates to the catalog service with the request
 * parameters verbatim and wraps the answer in a success {@link Result}.
 */
class AliyunCatalogControllerTest {

    private final AliyunCatalogService catalogService = mock(AliyunCatalogService.class);
    private final AliyunCatalogController controller = new AliyunCatalogController(catalogService);

    @Test
    void listRegionsDelegatesAndWrapsTheAnswer() {
        CloudRegionVO region = new CloudRegionVO();
        region.setRegionId("cn-hangzhou");
        region.setRegionName("Hangzhou");
        when(catalogService.listRegions(9L)).thenReturn(List.of(region));

        Result<List<CloudRegionVO>> result = controller.listRegions(9L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).containsExactly(region);
    }

    @Test
    void listInstancesPassesEveryParameterThrough() {
        CloudInstanceOptionVO instance = new CloudInstanceOptionVO();
        instance.setInstanceId("rmq-1");
        when(catalogService.listCloudInstances(9L, "cn-hangzhou", "order")).thenReturn(List.of(instance));

        Result<List<CloudInstanceOptionVO>> result = controller.listInstances(9L, "cn-hangzhou", "order");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(instance);
    }

    @Test
    void anAbsentSearchIsForwardedAsNull() {
        when(catalogService.listCloudInstances(9L, "cn-hangzhou", null)).thenReturn(List.of());

        Result<List<CloudInstanceOptionVO>> result = controller.listInstances(9L, "cn-hangzhou", null);

        assertThat(result.getData()).isEmpty();
    }
}
