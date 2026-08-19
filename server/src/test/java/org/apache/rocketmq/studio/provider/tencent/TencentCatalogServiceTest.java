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

import com.tencentcloudapi.trocket.v20230308.models.DescribeInstanceListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeInstanceResponse;
import com.tencentcloudapi.trocket.v20230308.models.Endpoint;
import com.tencentcloudapi.trocket.v20230308.models.InstanceItem;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentCatalogServiceTest {

    private static final String CREDENTIAL_ID = "cred-1";
    private static final String REGION = "ap-chengdu";

    @Mock
    private TencentClientFactory clientFactory;

    private TencentCatalogService service;

    @BeforeEach
    void setUp() {
        service = new TencentCatalogService(clientFactory);
    }

    @Test
    void listRegionsShouldContainAllSupportedRocketmq5RegionsTest() {
        List<CloudRegionVO> regions = service.listRegions(CREDENTIAL_ID);

        assertThat(regions).extracting(CloudRegionVO::getRegionId)
                .containsExactly(
                        "ap-guangzhou", "ap-shenzhen-fsi", "ap-nanjing", "ap-shanghai",
                        "ap-shanghai-fsi", "ap-shanghai-adc", "ap-hongkong", "ap-beijing",
                        "ap-chengdu", "ap-chongqing", "ap-singapore", "ap-bangkok",
                        "ap-jakarta", "ap-seoul", "ap-tokyo", "na-siliconvalley",
                        "na-ashburn", "eu-frankfurt", "sa-saopaulo");
    }

    @Test
    void listCloudInstancesShouldMapRocketmq5ItemsTest() {
        InstanceItem item = new InstanceItem();
        item.setInstanceId("rmq-abc");
        item.setInstanceName("chengdu-prod");
        item.setInstanceStatus("RUNNING");
        item.setTopicNum(3L);
        item.setGroupNum(2L);
        DescribeInstanceListResponse response = new DescribeInstanceListResponse();
        response.setData(new InstanceItem[]{item});
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any())).thenReturn(response);

        List<CloudInstanceOptionVO> instances = service.listCloudInstances(CREDENTIAL_ID, REGION, "PROD");

        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).getInstanceId()).isEqualTo("rmq-abc");
        assertThat(instances.get(0).getRegionId()).isEqualTo(REGION);
        assertThat(instances.get(0).getTopicCount()).isEqualTo(3);
    }

    @Test
    void listCloudInstancesShouldSkipNullItemsTest() {
        InstanceItem item = new InstanceItem();
        item.setInstanceId("rmq-valid");
        item.setInstanceName("valid-instance");
        DescribeInstanceListResponse response = new DescribeInstanceListResponse();
        response.setData(new InstanceItem[]{null, item});
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any())).thenReturn(response);

        List<CloudInstanceOptionVO> instances = service.listCloudInstances(CREDENTIAL_ID, REGION, null);

        assertThat(instances).extracting(CloudInstanceOptionVO::getInstanceId)
                .containsExactly("rmq-valid");
    }

    @Test
    void getCloudInstanceShouldMapOnlyOpenEndpointsTest() {
        Endpoint vpc = endpoint("VPC", "OPEN", "vpc.tencent:8080");
        Endpoint publicEndpoint = endpoint("PUBLIC", "OPEN", "public.tencent:8080");
        Endpoint closed = endpoint("PUBLIC", "CLOSE", "closed.tencent:8080");
        DescribeInstanceResponse response = new DescribeInstanceResponse();
        response.setInstanceId("rmq-abc");
        response.setInstanceName("chengdu-prod");
        response.setInstanceStatus("RUNNING");
        response.setEndpointList(new Endpoint[]{vpc, publicEndpoint, closed});
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any())).thenReturn(response);

        CloudInstanceDetailVO detail = service.getCloudInstance(CREDENTIAL_ID, REGION, "rmq-abc");

        assertThat(detail.getEndpoints()).hasSize(2);
        assertThat(detail.getEndpoints().get(0).getEndpointType()).isEqualTo("TCP_VPC");
        assertThat(detail.getEndpoints().get(1).getEndpointType()).isEqualTo("TCP_INTERNET");
    }

    private static Endpoint endpoint(String type, String status, String url) {
        Endpoint endpoint = new Endpoint();
        endpoint.setType(type);
        endpoint.setStatus(status);
        endpoint.setEndpointUrl(url);
        return endpoint;
    }
}
