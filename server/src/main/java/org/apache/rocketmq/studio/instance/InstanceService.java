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
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.audit.OperationAuditService;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final CloudCredentialRepository cloudCredentialRepository;
    private final InstanceProviderRegistry providerRegistry;
    private final MqAdminExtFactory adminFactory;
    private final OperationAuditService operationAuditService;

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
            instance.setResourceCountsAvailable(true);
        } catch (RuntimeException ex) {
            instance.setResourceCountsAvailable(false);
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
            case ALIYUN, TENCENT -> createCloudInstance(instance, vendor);
        }

        instance.setId(UUID.randomUUID().toString());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        InstanceVO saved = instanceRepository.save(instance);
        recordAudit("CREATE_INSTANCE", "INSTANCE", saved.getId(), null,
                instanceAuditDetail(saved));
        return saved;
    }

    private void createApacheInstance(InstanceVO instance) {
        instance.setVendor(InstanceVendor.APACHE);
        instance.setName(requireInstanceName(instance.getName()));
        instance.setEndpoint(requireValidEndpoint(instance.getEndpoint()));
        instance.setAdminCredentialRef(normalizeCredentialRef(instance.getAdminCredentialRef()));
        if (instance.getType() == null) {
            throw new BusinessException(400, "InstanceVO type is required");
        }
    }

    /**
     * Commercial instances are never created manually: the user picks a stored credential and
     * one of the cloud instances returned by the vendor catalog; endpoint is resolved from the
     * cloud instance detail (VPC endpoint preferred).
     */
    private void createCloudInstance(InstanceVO instance, InstanceVendor vendor) {
        instance.setVendor(vendor);
        if (instance.getEndpoint() != null && !instance.getEndpoint().isBlank()) {
            throw new BusinessException(400, "Commercial instances must be selected from the cloud catalog, endpoint cannot be set manually");
        }
        if (!StringUtils.hasText(instance.getCredentialId()) || !StringUtils.hasText(instance.getCloudInstanceId())
                || !StringUtils.hasText(instance.getRegionId())) {
            throw new BusinessException(400,
                    "credentialId, cloudInstanceId and regionId are required for " + vendor + " instances");
        }
        CloudCredentialVO credential = cloudCredentialRepository.findById(instance.getCredentialId())
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + instance.getCredentialId()));
        if (credential.getVendor() != vendor) {
            throw new BusinessException(400, "Cloud credential vendor does not match " + vendor);
        }
        CloudInstanceDetailVO detail = providerRegistry.catalogFor(vendor)
                .getCloudInstance(instance.getCredentialId(), instance.getRegionId(), instance.getCloudInstanceId());
        if (!StringUtils.hasText(instance.getName())) {
            instance.setName(detail.getInstanceName() != null && !detail.getInstanceName().isBlank()
                    ? detail.getInstanceName() : detail.getInstanceId());
        }
        instance.setName(requireInstanceName(instance.getName()));
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
        return switch (endpointType.toUpperCase(Locale.ROOT)) {
            case "TCP_VPC" -> 0;
            case "TCP_INTERNET" -> 1;
            default -> 2;
        };
    }


    private String requireValidEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(400, "InstanceVO endpoint is required");
        }
        String normalized = endpoint.trim();
        for (String address : normalized.split("[;,]", -1)) {
            if (address.isBlank()) {
                throw new BusinessException(400, "InstanceVO endpoint must not contain empty addresses");
            }
        }
        return normalized;
    }

    private String requireInstanceName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "InstanceVO name is required");
        }
        return name.trim();
    }

    private String normalizeCredentialRef(String credentialRef) {
        return StringUtils.hasText(credentialRef) ? credentialRef.trim() : null;
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

        InstanceVO updated = copyOf(existing);
        boolean cloudInstance = existing.getVendor() != null && existing.getVendor() != InstanceVendor.APACHE;
        if (instance.getName() != null) {
            updated.setName(requireInstanceName(instance.getName()));
        }
        if (!cloudInstance) {
            if (instance.getType() != null) {
                updated.setType(instance.getType());
            }
            if (instance.getEndpoint() != null) {
                updated.setEndpoint(requireValidEndpoint(instance.getEndpoint()));
            }
        }
        if (instance.getRemark() != null) {
            updated.setRemark(instance.getRemark());
        }
        if (!cloudInstance && instance.getAdminCredentialRef() != null) {
            updated.setAdminCredentialRef(normalizeCredentialRef(instance.getAdminCredentialRef()));
        }
        updated.setUpdatedAt(LocalDateTime.now());

        InstanceVO saved = instanceRepository.save(updated);
        releaseApacheClientIfChanged(existing, saved);
        recordAudit("UPDATE_INSTANCE", "INSTANCE", saved.getId(), null,
                instanceAuditDetail(saved));
        return saved;
    }

    public void deleteInstance(String id) {
        log.info("Deleting instance: {}", id);

        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "InstanceVO ID is required");
        }

        InstanceVO existing = instanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "InstanceVO not found: " + id));

        InstanceProvider provider = providerRegistry.forVendor(
                existing.getVendor() == null ? InstanceVendor.APACHE : existing.getVendor());
        int topicCount = provider.countTopics(id);
        int consumerGroupCount = provider.countGroups(id);
        if (topicCount > 0 || consumerGroupCount > 0) {
            throw new BusinessException(409, String.format(
                    "Cannot delete instance with managed resources: topics=%d, consumerGroups=%d",
                    topicCount, consumerGroupCount));
        }
        instanceRepository.deleteById(id);
        releaseApacheEndpointIfUnused(existing, null);
        recordAudit("DELETE_INSTANCE", "INSTANCE", id, null,
                instanceAuditDetail(existing));
    }

    private void requireInstance(InstanceVO instance) {
        if (instance == null) {
            throw new BusinessException(400, "Instance request is required");
        }
    }

    private String instanceAuditDetail(InstanceVO instance) {
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        return "name=" + instance.getName() + ", vendor=" + vendor + ", type=" + instance.getType();
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
                .adminCredentialRef(instance.getAdminCredentialRef())
                .regionId(instance.getRegionId())
                .topicCount(instance.getTopicCount())
                .consumerGroupCount(instance.getConsumerGroupCount())
                .build();
        copy.setId(instance.getId());
        copy.setCreatedAt(instance.getCreatedAt());
        copy.setUpdatedAt(instance.getUpdatedAt());
        return copy;
    }

    private void releaseApacheEndpointIfUnused(InstanceVO existing, String currentEndpoint) {
        if (!isApacheInstance(existing)) {
            return;
        }
        String oldEndpoint = normalizeEndpoint(existing.getEndpoint());
        if (oldEndpoint == null || oldEndpoint.equals(normalizeEndpoint(currentEndpoint))) {
            return;
        }
        releaseAdminClientIfUnused(existing, false);
    }

    private void releaseApacheClientIfChanged(InstanceVO existing, InstanceVO saved) {
        if (!isApacheInstance(existing)) {
            return;
        }
        String oldEndpoint = normalizeEndpoint(existing.getEndpoint());
        String currentEndpoint = normalizeEndpoint(saved.getEndpoint());
        String oldCredentialRef = normalizeCredentialRef(existing.getAdminCredentialRef());
        String currentCredentialRef = normalizeCredentialRef(saved.getAdminCredentialRef());
        if (Objects.equals(oldEndpoint, currentEndpoint)
                && Objects.equals(oldCredentialRef, currentCredentialRef)) {
            return;
        }
        releaseAdminClientIfUnused(existing, Objects.equals(oldEndpoint, currentEndpoint));
    }

    private void releaseAdminClientIfUnused(InstanceVO existing, boolean endpointRetainedByUpdatedInstance) {
        String oldEndpoint = normalizeEndpoint(existing.getEndpoint());
        if (oldEndpoint == null) {
            return;
        }
        String oldCredentialRef = normalizeCredentialRef(existing.getAdminCredentialRef());
        List<InstanceVO> endpointReferences = instanceRepository.findAll().stream()
                .filter(this::isApacheInstance)
                .filter(instance -> !existing.getId().equals(instance.getId()))
                .filter(instance -> oldEndpoint.equals(normalizeEndpoint(instance.getEndpoint())))
                .toList();
        boolean identityStillReferenced = endpointReferences.stream()
                .anyMatch(instance -> Objects.equals(oldCredentialRef,
                        normalizeCredentialRef(instance.getAdminCredentialRef())));
        if (identityStillReferenced) {
            return;
        }
        if (endpointRetainedByUpdatedInstance || !endpointReferences.isEmpty()) {
            adminFactory.release(oldEndpoint, oldCredentialRef);
        } else {
            adminFactory.release(oldEndpoint);
        }
    }

    private boolean isApacheInstance(InstanceVO instance) {
        return instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String normalizedEndpoint = MqAdminExtFactory.normalizeNamesrvAddr(endpoint);
        return normalizedEndpoint.isEmpty() ? null : normalizedEndpoint;
    }

    private void recordAudit(String operation, String resourceType, String resourceName,
                             String clusterId, String detail) {
        try {
            operationAuditService.record(operation, resourceType, resourceName, clusterId, detail, "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }

}
