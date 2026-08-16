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

import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponseBody;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliyunCatalogServiceTest {

    private static final String CREDENTIAL_ID = "cred-1";
    private static final String REGION = "cn-hangzhou";

    @Mock
    private AliyunClientFactory clientFactory;

    private AliyunCatalogService service;

    @BeforeEach
    void setUp() {
        service = new AliyunCatalogService(clientFactory);
    }

    @Test
    void listRegionsShouldSkipNullSdkRecords() {
        ListRegionsResponse response = ListRegionsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListRegionsResponseBody.builder()
                        .data(Arrays.asList(null, ListRegionsResponseBody.Data.builder()
                                .regionId("cn-hangzhou")
                                .supportRocketmqV5(true)
                                .build()))
                        .build())
                .build();
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(AliyunCatalogService.DEFAULT_REGION), any()))
                .thenReturn(response);

        assertThat(service.listRegions(CREDENTIAL_ID))
                .extracting(CloudRegionVO::getRegionId)
                .containsExactly("cn-hangzhou");
    }

    @Test
    void listRegionsShouldKeepOnlyRocketmqV5RegionsTest() {
        ListRegionsResponse response = ListRegionsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListRegionsResponseBody.builder()
                        .data(List.of(
                                ListRegionsResponseBody.Data.builder()
                                        .regionId("cn-shanghai").regionName("shanghai")
                                        .supportRocketmqV5(true).build(),
                                ListRegionsResponseBody.Data.builder()
                                        .regionId("cn-hangzhou").regionName("hangzhou")
                                        .supportRocketmqV5(true).build(),
                                ListRegionsResponseBody.Data.builder()
                                        .regionId("cn-beijing").regionName("beijing")
                                        .supportRocketmqV5(true).build(),
                                ListRegionsResponseBody.Data.builder()
                                        .regionId("cn-legacy").regionName("legacy")
                                        .supportRocketmqV5(false).build()))
                        .build())
                .build();
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(AliyunCatalogService.DEFAULT_REGION), any()))
                .thenReturn(response);

        List<CloudRegionVO> regions = service.listRegions(CREDENTIAL_ID);

        assertThat(regions).hasSize(3);
        assertThat(regions).extracting(CloudRegionVO::getRegionId)
                .containsExactly("cn-beijing", "cn-hangzhou", "cn-shanghai");
        assertThat(regions.get(1).getRegionName()).isEqualTo("hangzhou");
    }

    @Test
    void listCloudInstancesShouldSkipNullSdkRecords() {
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any()))
                .thenReturn(instancesResponse(Arrays.asList(null, instanceRow("rmq-a", "A"))));

        assertThat(service.listCloudInstances(CREDENTIAL_ID, REGION, null))
                .extracting(CloudInstanceOptionVO::getInstanceId)
                .containsExactly("rmq-a");
    }

    @Test
    void listCloudInstancesShouldAggregatePagesTest() {
        ListInstancesResponse firstPage = instancesResponse(
                instanceRows(AliyunConverters.PAGE_SIZE, 0), AliyunConverters.PAGE_SIZE + 3L);
        ListInstancesResponse secondPage = instancesResponse(instanceRows(3, AliyunConverters.PAGE_SIZE));
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any()))
                .thenReturn(firstPage, secondPage);

        List<CloudInstanceOptionVO> options = service.listCloudInstances(CREDENTIAL_ID, REGION, null);

        assertThat(options).hasSize(AliyunConverters.PAGE_SIZE + 3);
        verify(clientFactory, times(2)).call(eq(CREDENTIAL_ID), eq(REGION), any());
    }

    @Test
    void listCloudInstancesShouldContinuePastFallbackPageCapWhenTotalCountRequiresIt() {
        ListInstancesResponse fullPage = instancesResponse(
                instanceRows(AliyunConverters.PAGE_SIZE, 0), AliyunConverters.PAGE_SIZE * 6L + 1);
        ListInstancesResponse finalPage = instancesResponse(instanceRows(1, AliyunConverters.PAGE_SIZE * 6),
                AliyunConverters.PAGE_SIZE * 6L + 1);
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any()))
                .thenReturn(fullPage, fullPage, fullPage, fullPage, fullPage, fullPage, finalPage);

        List<CloudInstanceOptionVO> options = service.listCloudInstances(CREDENTIAL_ID, REGION, null);

        assertThat(options).hasSize(AliyunConverters.PAGE_SIZE * 6 + 1);
        verify(clientFactory, times(7)).call(eq(CREDENTIAL_ID), eq(REGION), any());
    }

    @Test
    void listCloudInstancesShouldApplySearchFilterTest() {
        List<ListInstancesResponseBody.List> rows = new ArrayList<>();
        rows.add(instanceRow("rmq-prod-001", "Production"));
        rows.add(instanceRow("rmq-test-002", "Staging"));
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any()))
                .thenReturn(instancesResponse(rows));

        List<CloudInstanceOptionVO> byId = service.listCloudInstances(CREDENTIAL_ID, REGION, "TEST-002");
        List<CloudInstanceOptionVO> byName = service.listCloudInstances(CREDENTIAL_ID, REGION, "staging");

        assertThat(byId).hasSize(1);
        assertThat(byId.get(0).getInstanceId()).isEqualTo("rmq-test-002");
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).getInstanceName()).isEqualTo("Staging");
    }

    @Test
    void listCloudInstancesShouldRequireRegionTest() {
        assertThatThrownBy(() -> service.listCloudInstances(CREDENTIAL_ID, " ", null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void getCloudInstanceShouldMapEndpointsTest() {
        GetInstanceResponse response = GetInstanceResponse.create().toBuilder()
                .statusCode(200)
                .body(GetInstanceResponseBody.builder()
                        .data(GetInstanceResponseBody.Data.builder()
                                .instanceId("rmq-cn-001")
                                .instanceName("prod")
                                .status("RUNNING")
                                .regionId(REGION)
                                .remark("prod instance")
                                .networkInfo(GetInstanceResponseBody.NetworkInfo.builder()
                                        .endpoints(List.of(
                                                GetInstanceResponseBody.Endpoints.builder()
                                                        .endpointType("TCP_INTERNET")
                                                        .endpointUrl("rmq-cn-001.rmq.aliyuncs.com:8080")
                                                        .build(),
                                                GetInstanceResponseBody.Endpoints.builder()
                                                        .endpointType("TCP_VPC")
                                                        .endpointUrl("rmq-cn-001-vpc.rmq.aliyuncs.com:8080")
                                                        .build()))
                                        .build())
                                .build())
                        .build())
                .build();
        when(clientFactory.call(eq(CREDENTIAL_ID), eq(REGION), any())).thenReturn(response);

        CloudInstanceDetailVO detail = service.getCloudInstance(CREDENTIAL_ID, REGION, "rmq-cn-001");

        assertThat(detail.getInstanceId()).isEqualTo("rmq-cn-001");
        assertThat(detail.getStatus()).isEqualTo("RUNNING");
        assertThat(detail.getRemark()).isEqualTo("prod instance");
        assertThat(detail.getEndpoints()).hasSize(2);
        assertThat(detail.getEndpoints().get(0).getEndpointType()).isEqualTo("TCP_INTERNET");
        assertThat(detail.getEndpoints().get(1).getEndpointUrl())
                .isEqualTo("rmq-cn-001-vpc.rmq.aliyuncs.com:8080");
    }

    private static List<ListInstancesResponseBody.List> instanceRows(int count, int idOffset) {
        List<ListInstancesResponseBody.List> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(instanceRow(String.format("rmq-instance-%04d", idOffset + i), "instance-" + i));
        }
        return rows;
    }

    private static ListInstancesResponseBody.List instanceRow(String instanceId, String instanceName) {
        return ListInstancesResponseBody.List.builder()
                .instanceId(instanceId)
                .instanceName(instanceName)
                .status("RUNNING")
                .regionId("cn-hangzhou")
                .topicCount(3L)
                .groupCount(2L)
                .remark("remark-" + instanceId)
                .build();
    }

    private static ListInstancesResponse instancesResponse(List<ListInstancesResponseBody.List> rows) {
        return instancesResponse(rows, (long) rows.size());
    }

    private static ListInstancesResponse instancesResponse(List<ListInstancesResponseBody.List> rows, long totalCount) {
        return ListInstancesResponse.create().toBuilder()
                .statusCode(200)
                .body(ListInstancesResponseBody.builder()
                        .data(ListInstancesResponseBody.Data.builder()
                                .list(rows)
                                .pageNumber(1L)
                                .pageSize((long) AliyunConverters.PAGE_SIZE)
                                .totalCount(totalCount)
                                .build())
                        .build())
                .build();
    }
}
