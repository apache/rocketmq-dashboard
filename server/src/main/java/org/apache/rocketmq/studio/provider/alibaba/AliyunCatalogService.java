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

import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.GetInstanceResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponseBody;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.CloudCatalogProvider;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Aliyun cloud catalog: regions and commercial RocketMQ 5.x instances visible to a credential.
 */
@RequiredArgsConstructor
@Component
public class AliyunCatalogService implements CloudCatalogProvider {

    /**
     * Region used to reach the control plane for region-independent operations such as ListRegions.
     */
    static final String DEFAULT_REGION = "cn-hangzhou";

    private final AliyunClientFactory clientFactory;

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.ALIYUN;
    }

    @Override
    public List<CloudRegionVO> listRegions(String credentialId) {
        requireNonBlank(credentialId, "credentialId");
        ListRegionsResponse response = clientFactory.call(credentialId, DEFAULT_REGION,
                client -> client.listRegions(ListRegionsRequest.builder().build()));
        ListRegionsResponseBody body = response == null ? null : response.getBody();
        List<ListRegionsResponseBody.Data> data = body == null ? null : body.getData();
        List<CloudRegionVO> regions = new ArrayList<>();
        if (data == null) {
            return regions;
        }
        for (ListRegionsResponseBody.Data item : data) {
            if (item != null && Boolean.TRUE.equals(item.getSupportRocketmqV5())) {
                regions.add(AliyunConverters.toRegionVO(item));
            }
        }
        regions.sort(Comparator.comparing(
                CloudRegionVO::getRegionId, Comparator.nullsLast(Comparator.naturalOrder())));
        return regions;
    }

    @Override
    public List<CloudInstanceOptionVO> listCloudInstances(String credentialId, String regionId, String search) {
        requireNonBlank(credentialId, "credentialId");
        requireNonBlank(regionId, "regionId");
        List<ListInstancesResponseBody.List> all = fetchAllInstances(credentialId, regionId);
        List<CloudInstanceOptionVO> options = new ArrayList<>();
        for (ListInstancesResponseBody.List item : all) {
            if (item == null) {
                continue;
            }
            CloudInstanceOptionVO vo = AliyunConverters.toInstanceOptionVO(item);
            if (matchesSearch(search, vo)) {
                options.add(vo);
            }
        }
        return options;
    }

    @Override
    public CloudInstanceDetailVO getCloudInstance(String credentialId, String regionId, String cloudInstanceId) {
        requireNonBlank(credentialId, "credentialId");
        requireNonBlank(regionId, "regionId");
        requireNonBlank(cloudInstanceId, "cloudInstanceId");
        GetInstanceRequest request = GetInstanceRequest.builder().instanceId(cloudInstanceId).build();
        GetInstanceResponse response = clientFactory.call(credentialId, regionId,
                client -> client.getInstance(request));
        GetInstanceResponseBody body = response == null ? null : response.getBody();
        GetInstanceResponseBody.Data data = body == null ? null : body.getData();
        if (data == null) {
            throw new BusinessException(404, "Aliyun instance not found: " + cloudInstanceId);
        }
        return AliyunConverters.toInstanceDetailVO(data);
    }

    private List<ListInstancesResponseBody.List> fetchAllInstances(String credentialId, String regionId) {
        List<ListInstancesResponseBody.List> all = new ArrayList<>();
        for (int page = 1; page <= AliyunConverters.MAX_PAGES; page++) {
            ListInstancesRequest request = ListInstancesRequest.builder()
                    .pageNumber(page)
                    .pageSize(AliyunConverters.PAGE_SIZE)
                    .build();
            ListInstancesResponse response = clientFactory.call(credentialId, regionId,
                    client -> client.listInstances(request));
            ListInstancesResponseBody body = response == null ? null : response.getBody();
            ListInstancesResponseBody.Data data = body == null ? null : body.getData();
            List<ListInstancesResponseBody.List> list = data == null ? null : data.getList();
            if (list == null || list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (list.size() < AliyunConverters.PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    private static boolean matchesSearch(String search, CloudInstanceOptionVO vo) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        boolean idMatches = vo.getInstanceId() != null
                && vo.getInstanceId().toLowerCase(Locale.ROOT).contains(needle);
        boolean nameMatches = vo.getInstanceName() != null
                && vo.getInstanceName().toLowerCase(Locale.ROOT).contains(needle);
        return idMatches || nameMatches;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, name + " is required");
        }
    }
}
