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

import com.tencentcloudapi.trocket.v20230308.models.DescribeInstanceListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeInstanceListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeInstanceRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeInstanceResponse;
import com.tencentcloudapi.trocket.v20230308.models.Endpoint;
import com.tencentcloudapi.trocket.v20230308.models.InstanceItem;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.CloudCatalogProvider;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tencent Cloud RocketMQ 5.x catalog backed by Trocket v20230308 OpenAPI.
 */
@RequiredArgsConstructor
@Component
public class TencentCatalogService implements CloudCatalogProvider {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 100;
    static final List<CloudRegionVO> SUPPORTED_REGIONS = List.of(
            region("ap-beijing", "Beijing"),
            region("ap-chengdu", "Chengdu"),
            region("ap-chongqing", "Chongqing"),
            region("ap-guangzhou", "Guangzhou"),
            region("ap-hongkong", "Hong Kong"),
            region("ap-mumbai", "Mumbai"),
            region("ap-nanjing", "Nanjing"),
            region("ap-seoul", "Seoul"),
            region("ap-shanghai", "Shanghai"),
            region("ap-shanghai-fsi", "Shanghai Finance"),
            region("ap-shenzhen-fsi", "Shenzhen Finance"),
            region("ap-singapore", "Singapore"),
            region("ap-tokyo", "Tokyo"),
            region("ap-bangkok", "Bangkok"),
            region("ap-jakarta", "Jakarta"),
            region("eu-frankfurt", "Frankfurt"),
            region("na-ashburn", "Ashburn"),
            region("na-siliconvalley", "Silicon Valley"),
            region("na-toronto", "Toronto"));

    private final TencentClientFactory clientFactory;

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.TENCENT;
    }

    @Override
    public List<CloudRegionVO> listRegions(String credentialId) {
        requireNonBlank(credentialId, "credentialId");
        return SUPPORTED_REGIONS;
    }

    @Override
    public List<CloudInstanceOptionVO> listCloudInstances(String credentialId, String regionId, String search) {
        requireNonBlank(credentialId, "credentialId");
        requireNonBlank(regionId, "regionId");
        List<CloudInstanceOptionVO> instances = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeInstanceListRequest request = new DescribeInstanceListRequest();
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeInstanceListResponse response = clientFactory.call(credentialId, regionId,
                    client -> client.DescribeInstanceList(request));
            InstanceItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            for (InstanceItem item : data) {
                CloudInstanceOptionVO option = toInstanceOption(item, regionId);
                if (matchesSearch(search, option)) {
                    instances.add(option);
                }
            }
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        return instances;
    }

    @Override
    public CloudInstanceDetailVO getCloudInstance(String credentialId, String regionId, String cloudInstanceId) {
        requireNonBlank(credentialId, "credentialId");
        requireNonBlank(regionId, "regionId");
        requireNonBlank(cloudInstanceId, "cloudInstanceId");
        DescribeInstanceRequest request = new DescribeInstanceRequest();
        request.setInstanceId(cloudInstanceId);
        DescribeInstanceResponse response = clientFactory.call(credentialId, regionId,
                client -> client.DescribeInstance(request));
        if (response == null || response.getInstanceId() == null) {
            throw new BusinessException(404, "Tencent Cloud RocketMQ 5.x instance not found: " + cloudInstanceId);
        }
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setInstanceId(response.getInstanceId());
        detail.setInstanceName(response.getInstanceName());
        detail.setStatus(response.getInstanceStatus());
        detail.setRegionId(regionId);
        detail.setRemark(response.getRemark());
        detail.setEndpoints(toEndpoints(response.getEndpointList()));
        return detail;
    }

    private static CloudInstanceOptionVO toInstanceOption(InstanceItem item, String regionId) {
        CloudInstanceOptionVO option = new CloudInstanceOptionVO();
        option.setInstanceId(item.getInstanceId());
        option.setInstanceName(item.getInstanceName());
        option.setStatus(item.getInstanceStatus());
        option.setRegionId(regionId);
        option.setTopicCount(toInteger(item.getTopicNum()));
        option.setGroupCount(toInteger(item.getGroupNum()));
        option.setRemark(item.getRemark());
        return option;
    }

    private static List<CloudInstanceDetailVO.CloudEndpoint> toEndpoints(Endpoint[] endpoints) {
        List<CloudInstanceDetailVO.CloudEndpoint> result = new ArrayList<>();
        if (endpoints == null) {
            return result;
        }
        for (Endpoint endpoint : endpoints) {
            if (endpoint != null && "OPEN".equalsIgnoreCase(endpoint.getStatus())) {
                result.add(new CloudInstanceDetailVO.CloudEndpoint(
                        endpointType(endpoint.getType()), endpoint.getEndpointUrl()));
            }
        }
        return result;
    }

    private static String endpointType(String type) {
        if ("VPC".equalsIgnoreCase(type)) {
            return "TCP_VPC";
        }
        if ("PUBLIC".equalsIgnoreCase(type)) {
            return "TCP_INTERNET";
        }
        return type;
    }

    private static Integer toInteger(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private static boolean matchesSearch(String search, CloudInstanceOptionVO option) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(option.getInstanceId(), needle)
                || containsIgnoreCase(option.getInstanceName(), needle);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static CloudRegionVO region(String id, String name) {
        CloudRegionVO region = new CloudRegionVO();
        region.setRegionId(id);
        region.setRegionName(name);
        return region;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, name + " is required");
        }
    }
}
