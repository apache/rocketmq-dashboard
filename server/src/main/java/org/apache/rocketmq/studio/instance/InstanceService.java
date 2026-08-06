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

package org.apache.rocketmq.studio.instance;

import org.springframework.util.StringUtils;

import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final CloudCredentialRepository cloudCredentialRepository;
    private final InstanceProviderRegistry providerRegistry;

    public List<InstanceVO> listInstances(InstanceType type, String search) {
        log.debug("Listing instances, type={}, search={}", type, search);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();

        List<InstanceVO> instances;
        if (type != null && normalizedSearch != null) {
            instances = instanceRepository.findByTypeAndSearch(type, normalizedSearch);
        } else if (type != null) {
            instances = instanceRepository.findByType(type);
        } else if (normalizedSearch != null) {
            instances = instanceRepository.search(normalizedSearch);
        } else {
            instances = instanceRepository.findAll();
        }
        instances.forEach(this::fillCounts);
        return instances;
    }

    /**
     * Resource counts live on the vendor side (cloud APIs) or in the local tables (Apache),
     * so resolve them uniformly through the vendor provider.
     */
    private void fillCounts(InstanceVO instance) {
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        try {
            InstanceProvider provider = providerRegistry.forVendor(vendor);
            instance.setTopicCount(provider.countTopics(instance.getId()));
            instance.setConsumerGroupCount(provider.countGroups(instance.getId()));
        } catch (RuntimeException ex) {
            log.warn("Failed to load resource counts for instance {}: {}",
                    instance.getId(), ex.getMessage());
        }
    }

    public InstanceVO createInstance(InstanceVO instance) {
        requireInstance(instance);
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        log.info("Creating instance: name={}, vendor={}", instance.getName(), vendor);

        switch (vendor) {
            case APACHE -> createApacheInstance(instance);
            case ALIYUN -> createAliyunInstance(instance);
            case TENCENT -> throw new BusinessException(501, "Tencent Cloud instance is not supported yet");
        }

        instance.setId(UUID.randomUUID().toString());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
    }

    private void createApacheInstance(InstanceVO instance) {
        instance.setVendor(InstanceVendor.APACHE);
        if (instance.getName() == null || instance.getName().isBlank()) {
            throw new BusinessException(400, "InstanceVO name is required");
        }
        if (instance.getEndpoint() == null || instance.getEndpoint().isBlank()) {
            throw new BusinessException(400, "InstanceVO endpoint is required");
        }
    }

    /**
     * Commercial instances are never created manually: the user picks a stored credential and
     * one of the cloud instances returned by the vendor catalog; endpoint is resolved from the
     * cloud instance detail (VPC endpoint preferred).
     */
    private void createAliyunInstance(InstanceVO instance) {
        instance.setVendor(InstanceVendor.ALIYUN);
        if (instance.getEndpoint() != null && !instance.getEndpoint().isBlank()) {
            throw new BusinessException(400, "Commercial instances must be selected from the cloud catalog, endpoint cannot be set manually");
        }
        if (!StringUtils.hasText(instance.getCredentialId()) || !StringUtils.hasText(instance.getCloudInstanceId())
                || !StringUtils.hasText(instance.getRegionId())) {
            throw new BusinessException(400, "credentialId, cloudInstanceId and regionId are required for Aliyun instances");
        }
        CloudCredentialVO credential = cloudCredentialRepository.findById(instance.getCredentialId())
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + instance.getCredentialId()));
        if (credential.getVendor() != InstanceVendor.ALIYUN) {
            throw new BusinessException(400, "Cloud credential vendor does not match ALIYUN");
        }
        CloudInstanceDetailVO detail = providerRegistry.catalogFor(InstanceVendor.ALIYUN)
                .getCloudInstance(instance.getCredentialId(), instance.getRegionId(), instance.getCloudInstanceId());
        if (instance.getName() == null || instance.getName().isBlank()) {
            instance.setName(detail.getInstanceName() != null && !detail.getInstanceName().isBlank()
                    ? detail.getInstanceName() : detail.getInstanceId());
        }
        instance.setType(InstanceType.PROXY);
        instance.setEndpoint(resolveEndpoint(detail));
    }

    private String resolveEndpoint(CloudInstanceDetailVO detail) {
        if (detail.getEndpoints() == null || detail.getEndpoints().isEmpty()) {
            throw new BusinessException(502, "Cloud instance has no endpoint: " + detail.getInstanceId());
        }
        return detail.getEndpoints().stream()
                .filter(endpoint -> endpoint.getEndpointUrl() != null && !endpoint.getEndpointUrl().isBlank())
                .sorted((a, b) -> Integer.compare(endpointPriority(a.getEndpointType()), endpointPriority(b.getEndpointType())))
                .map(CloudInstanceDetailVO.CloudEndpoint::getEndpointUrl)
                .findFirst()
                .orElseThrow(() -> new BusinessException(502, "Cloud instance has no usable endpoint: " + detail.getInstanceId()));
    }

    private int endpointPriority(String endpointType) {
        if (endpointType == null) {
            return 2;
        }
        return switch (endpointType.toUpperCase()) {
            case "TCP_VPC" -> 0;
            case "TCP_INTERNET" -> 1;
            default -> 2;
        };
    }


    public InstanceVO updateInstance(InstanceVO instance) {
        requireInstance(instance);
        log.info("Updating instance: {}", instance.getId());

        if (instance.getId() == null || instance.getId().isBlank()) {
            throw new BusinessException(400, "InstanceVO ID is required");
        }

        InstanceVO existing = instanceRepository.findById(instance.getId())
                .orElseThrow(() -> new BusinessException(404, "InstanceVO not found: " + instance.getId()));

        if (instance.getName() != null && instance.getName().isBlank()) {
            throw new BusinessException(400, "InstanceVO name is required");
        }
        if (instance.getEndpoint() != null && instance.getEndpoint().isBlank()) {
            throw new BusinessException(400, "InstanceVO endpoint is required");
        }

        InstanceVO updated = copyOf(existing);
        boolean cloudInstance = existing.getVendor() != null && existing.getVendor() != InstanceVendor.APACHE;
        if (instance.getName() != null) {
            updated.setName(instance.getName());
        }
        if (!cloudInstance) {
            if (instance.getType() != null) {
                updated.setType(instance.getType());
            }
            if (instance.getEndpoint() != null) {
                updated.setEndpoint(instance.getEndpoint());
            }
        }
        if (instance.getRemark() != null) {
            updated.setRemark(instance.getRemark());
        }
        updated.setUpdatedAt(LocalDateTime.now());

        return instanceRepository.save(updated);
    }

    public void deleteInstance(String id) {
        log.info("Deleting instance: {}", id);

        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "InstanceVO ID is required");
        }

        InstanceVO existing = instanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "InstanceVO not found: " + id));

        if (existing.getTopicCount() > 0 || existing.getConsumerGroupCount() > 0) {
            throw new BusinessException(409, String.format(
                    "Cannot delete instance with managed resources: topics=%d, consumerGroups=%d",
                    existing.getTopicCount(), existing.getConsumerGroupCount()));
        }
        instanceRepository.deleteById(id);
    }

    private void requireInstance(InstanceVO instance) {
        if (instance == null) {
            throw new BusinessException(400, "Instance request is required");
        }
    }

    private InstanceVO copyOf(InstanceVO instance) {
        InstanceVO copy = InstanceVO.builder()
                .name(instance.getName())
                .remark(instance.getRemark())
                .type(instance.getType())
                .endpoint(instance.getEndpoint())
                .vendor(instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor())
                .cloudInstanceId(instance.getCloudInstanceId())
                .credentialId(instance.getCredentialId())
                .regionId(instance.getRegionId())
                .topicCount(instance.getTopicCount())
                .consumerGroupCount(instance.getConsumerGroupCount())
                .build();
        copy.setId(instance.getId());
        copy.setCreatedAt(instance.getCreatedAt());
        copy.setUpdatedAt(instance.getUpdatedAt());
        return copy;
    }
}
