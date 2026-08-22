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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.RegionNames;
import org.apache.rocketmq.studio.provider.CloudCatalogProvider;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final CloudCredentialRepository cloudCredentialRepository;
    private final InstanceProviderRegistry providerRegistry;
    private final MqAdminExtFactory adminFactory;
    private final OperationAuditService operationAuditService;
    private final SettingsRepository settingsRepository;
    private final RegionNames regionNames;

    static final int COUNT_PARALLELISM = 8;
    static final long COUNT_TIMEOUT_SECONDS = 3;
    private static final int MAX_INSTANCE_PAGE_SIZE = 100;

    private final ExecutorService countExecutor = Executors.newFixedThreadPool(COUNT_PARALLELISM, runnable -> {
        Thread thread = new Thread(runnable, "instance-resource-counts");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdownCountExecutor() {
        countExecutor.shutdownNow();
    }

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
        fillCountsInParallel(instances);
        instances.forEach(instance -> instance.setRegionName(regionNames.resolve(instance.getRegionId())));
        List<InstanceVO> sorted = new ArrayList<>(instances);
        sorted.sort(Comparator
                .comparing((InstanceVO instance) ->
                        instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE ? 0 : 1)
                .thenComparing(instance -> instance.getVendor() == null ? "" : instance.getVendor().name())
                .thenComparing(instance -> instance.getRegionId() == null ? "" : instance.getRegionId())
                .thenComparing(InstanceVO::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    public PageResult<InstanceVO> listInstances(InstanceType type, String search, int page,
                                                int pageSize) {
        validateListPagination(page, pageSize);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        long total = instanceRepository.count(type, normalizedSearch);
        if (total == 0) {
            return PageResult.empty(page, pageSize);
        }
        List<InstanceVO> sorted = listInstances(type, normalizedSearch);
        int fromIndex = (int) Math.min((long) (page - 1) * pageSize, sorted.size());
        int toIndex = Math.min(fromIndex + pageSize, sorted.size());
        return PageResult.of(new ArrayList<>(sorted.subList(fromIndex, toIndex)), total, page, pageSize);
    }

    private static void validateListPagination(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > MAX_INSTANCE_PAGE_SIZE) {
            throw new BusinessException(400,
                    "pageSize must be between 1 and " + MAX_INSTANCE_PAGE_SIZE);
        }
    }

    /**
     * Fans out per-instance resource counts on a bounded executor. Cloud vendors resolve counts
     * through remote OpenAPIs, so a slow instance only degrades its own row (counts marked
     * unavailable) instead of blocking the whole list response.
     */
    private void fillCountsInParallel(List<InstanceVO> instances) {
        if (instances.isEmpty()) {
            return;
        }
        List<Callable<Void>> tasks = instances.stream()
                .<Callable<Void>>map(instance -> () -> {
                    fillCounts(instance);
                    return null;
                })
                .toList();
        List<Future<Void>> futures;
        try {
            // A single deadline for the whole batch. Waiting per future would let one hung
            // vendor add COUNT_TIMEOUT_SECONDS to the response for every instance.
            futures = countExecutor.invokeAll(tasks, COUNT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            instances.forEach(instance -> instance.setResourceCountsAvailable(false));
            return;
        }
        for (int i = 0; i < futures.size(); i++) {
            InstanceVO instance = instances.get(i);
            Future<Void> future = futures.get(i);
            if (future.isCancelled()) {
                // Missed the shared deadline; invokeAll already interrupted the task.
                instance.setResourceCountsAvailable(false);
                log.warn("Resource counts timed out after {}s for instance {}",
                        COUNT_TIMEOUT_SECONDS, instance.getId());
            } else {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    instance.setResourceCountsAvailable(false);
                } catch (ExecutionException ex) {
                    instance.setResourceCountsAvailable(false);
                    log.warn("Failed to load resource counts for instance {}: {}",
                            instance.getId(), ex.getMessage());
                }
            }
        }
    }

    /**
     * Resource counts live on the vendor side (cloud APIs) or in the local tables (Apache),
     * so resolve them uniformly through the vendor provider.
     */
    private void fillCounts(InstanceVO instance) {
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        try {
            InstanceProvider provider = providerRegistry.forVendor(vendor);
            instance.setTopicCount(provider.countTopics(String.valueOf(instance.getId())));
            instance.setConsumerGroupCount(provider.countGroups(String.valueOf(instance.getId())));
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

        requireUniqueInstanceName(instance.getName(), null);
        instance.setGmtCreate(LocalDateTime.now());
        instance.setGmtModified(LocalDateTime.now());
        InstanceVO saved = instanceRepository.save(instance);
        recordAudit("CREATE_INSTANCE", "INSTANCE", String.valueOf(saved.getId()), null,
                instanceAuditDetail(saved));
        return saved;
    }

    /**
     * Imports every cloud instance visible to the credential by walking all catalog regions.
     * Remarks are resolved from the cloud instance detail during creation. Instances whose
     * resolved name already exists are skipped; per-instance failures are collected instead
     * of aborting the batch.
     */
    public CloudImportResultVO importCloudInstances(InstanceVendor vendor, Long credentialId) {
        if (vendor == null || vendor == InstanceVendor.APACHE) {
            throw new BusinessException(400, "Import is only supported for cloud vendors");
        }
        if (credentialId == null) {
            throw new BusinessException(400, "credentialId is required");
        }
        CloudCredentialVO credential = cloudCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + credentialId));
        if (credential.getVendor() != vendor) {
            throw new BusinessException(400, "Cloud credential vendor does not match " + vendor);
        }
        CloudCatalogProvider catalog = providerRegistry.catalogFor(vendor);

        int discovered = 0;
        int imported = 0;
        int skipped = 0;
        List<String> failed = new ArrayList<>();
        for (CloudRegionVO region : catalog.listRegions(credentialId)) {
            if (region == null || !StringUtils.hasText(region.getRegionId())) {
                continue;
            }
            List<CloudInstanceOptionVO> options;
            try {
                options = catalog.listCloudInstances(credentialId, region.getRegionId(), null);
            } catch (BusinessException ex) {
                failed.add(region.getRegionId() + ": " + ex.getMessage());
                continue;
            }
            for (CloudInstanceOptionVO option : options) {
                if (option == null || !StringUtils.hasText(option.getInstanceId())) {
                    continue;
                }
                discovered++;
                InstanceVO request = InstanceVO.builder()
                        .vendor(vendor)
                        .credentialId(credentialId)
                        .regionId(region.getRegionId())
                        .cloudInstanceId(option.getInstanceId())
                        .name(option.getInstanceId())
                        .build();
                try {
                    createInstance(request);
                    imported++;
                } catch (BusinessException ex) {
                    if (ex.getMessage() != null && ex.getMessage().startsWith("Instance name already exists")) {
                        skipped++;
                    } else {
                        failed.add(option.getInstanceId() + ": " + ex.getMessage());
                    }
                }
            }
        }
        log.info("Cloud import finished: vendor={}, credentialId={}, discovered={}, imported={}, skipped={}, failed={}",
                vendor, credentialId, discovered, imported, skipped, failed.size());
        recordAudit("IMPORT_CLOUD_INSTANCES", "INSTANCE", String.valueOf(credentialId), null,
                "vendor=" + vendor + ", imported=" + imported + ", skipped=" + skipped
                        + ", failed=" + failed.size());
        return CloudImportResultVO.builder()
                .discovered(discovered)
                .imported(imported)
                .skipped(skipped)
                .failed(failed)
                .build();
    }

    private void requireUniqueInstanceName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        instanceRepository.findByName(name).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException(400, "Instance name already exists: " + name);
            }
        });
    }

    /**
     * Resolves the external instance identifier (globally unique instance name, with a
     * numeric primary-key fallback) to the internal database id.
     */
    public Long resolveInstanceId(String instanceId) {
        return instanceRepository.findByIdentifier(instanceId)
                .map(InstanceVO::getId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
    }

    /**
     * Normalizes any accepted identifier (instance name or legacy numeric id) to the
     * canonical instance ID (the globally unique instance name). Unknown values pass
     * through unchanged.
     */
    public String normalizeIdentifier(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            return instanceId;
        }
        return instanceRepository.findByIdentifier(instanceId)
                .map(InstanceVO::getName)
                .orElse(instanceId);
    }

    private void createApacheInstance(InstanceVO instance) {
        instance.setVendor(InstanceVendor.APACHE);
        instance.setName(requireInstanceName(instance.getName()));
        instance.setEndpoint(requireValidEndpoint(instance.getEndpoint()));
        instance.setAdminCredentialRef(normalizeCredentialRef(instance.getAdminCredentialRef()));
        if (instance.getType() == null) {
            throw new BusinessException(400, "InstanceVO type is required");
        }
        if (instance.getType() == InstanceType.CLOUD) {
            throw new BusinessException(400, "CLOUD type is reserved for vendor-managed instances");
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
        if (instance.getCredentialId() == null || !StringUtils.hasText(instance.getCloudInstanceId())
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
        if (detail == null) {
            throw new BusinessException(502,
                    "Cloud instance details unavailable: " + instance.getCloudInstanceId());
        }
        if (!StringUtils.hasText(instance.getName())) {
            instance.setName(detail.getInstanceId());
        }
        instance.setName(requireInstanceName(instance.getName()));
        instance.setType(InstanceType.CLOUD);
        instance.setEndpoint(resolveEndpoint(detail));
        if (!StringUtils.hasText(instance.getRemark()) && StringUtils.hasText(detail.getRemark())) {
            instance.setRemark(detail.getRemark());
        }
    }

    private String resolveEndpoint(CloudInstanceDetailVO detail) {
        if (detail.getEndpoints() == null || detail.getEndpoints().isEmpty()) {
            throw new BusinessException(502, "Cloud instance has no endpoint: " + detail.getInstanceId());
        }
        return detail.getEndpoints().stream()
                .filter(Objects::nonNull)
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
        String trimmed = name.trim();
        if (trimmed.length() > 64) {
            throw new BusinessException(400, "InstanceVO name must not exceed 64 characters");
        }
        return trimmed;
    }

    private String normalizeCredentialRef(String credentialRef) {
        return StringUtils.hasText(credentialRef) ? credentialRef.trim() : null;
    }

    public InstanceVO updateInstance(InstanceVO instance) {
        requireInstance(instance);
        log.info("Updating instance: {}", instance.getId());

        if (instance.getId() == null) {
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
            String requestedName = requireInstanceName(instance.getName());
            if (!requestedName.equals(existing.getName())) {
                throw new BusinessException(400, "Instance ID cannot be changed after creation");
            }
        }
        if (!cloudInstance) {
            if (instance.getType() != null) {
                if (instance.getType() == InstanceType.CLOUD) {
                    throw new BusinessException(400, "CLOUD type is reserved for vendor-managed instances");
                }
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
        updated.setGmtModified(LocalDateTime.now());

        InstanceVO saved = instanceRepository.save(updated);
        releaseApacheClientIfChanged(existing, saved);
        recordAudit("UPDATE_INSTANCE", "INSTANCE", String.valueOf(saved.getId()), null,
                instanceAuditDetail(saved));
        return saved;
    }

    @Transactional
    public void deleteInstance(Long id) {
        log.info("Deleting instance: {}", id);

        if (id == null) {
            throw new BusinessException(400, "InstanceVO ID is required");
        }

        InstanceVO existing = instanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "InstanceVO not found: " + id));

        InstanceVendor vendor = existing.getVendor() == null ? InstanceVendor.APACHE : existing.getVendor();
        if (vendor == InstanceVendor.APACHE) {
            InstanceProvider provider = providerRegistry.forVendor(InstanceVendor.APACHE);
            int topicCount = provider.countTopics(String.valueOf(id));
            int consumerGroupCount = provider.countGroups(String.valueOf(id));
            if (topicCount > 0 || consumerGroupCount > 0) {
                throw new BusinessException(409, String.format(
                        "Cannot delete instance with managed resources: topics=%d, consumerGroups=%d",
                        topicCount, consumerGroupCount));
            }
        }
        if (!instanceRepository.deleteById(id)) {
            throw new BusinessException(404, "InstanceVO not found: " + id);
        }
        removeDataSourceBindings(existing.getName());
        releaseApacheEndpointIfUnused(existing, null);
        recordAudit("DELETE_INSTANCE", "INSTANCE", String.valueOf(id), null,
                instanceAuditDetail(existing));
    }

    /**
     * Deletes the selected instances one by one, collecting per-instance failures (for example
     * an APACHE instance that still owns topics/groups) instead of aborting the whole batch.
     */
    public BatchDeleteResultVO deleteInstances(List<String> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            throw new BusinessException(400, "Instance IDs are required");
        }
        List<String> normalizedIds = instanceIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(instanceId -> !instanceId.isEmpty())
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new BusinessException(400, "Instance IDs are required");
        }
        int deleted = 0;
        List<String> failed = new ArrayList<>();
        for (String instanceId : normalizedIds) {
            try {
                deleteInstance(resolveInstanceId(instanceId));
                deleted++;
            } catch (BusinessException ex) {
                failed.add(instanceId + ": " + ex.getMessage());
            }
        }
        return BatchDeleteResultVO.builder().deleted(deleted).failed(failed).build();
    }

    private void removeDataSourceBindings(String instanceId) {
        for (DataSourceVO dataSource : settingsRepository.findAllDataSources()) {
            List<String> instanceIds = dataSource.getInstanceIds();
            if (instanceIds == null || !instanceIds.contains(instanceId)) {
                continue;
            }
            dataSource.setInstanceIds(instanceIds.stream()
                    .filter(candidate -> !instanceId.equals(candidate))
                    .toList());
            if (!settingsRepository.replaceDataSource(dataSource)) {
                log.warn("Metrics data source {} disappeared while removing instance binding {}",
                        dataSource.getKey(), instanceId);
            }
        }
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
        copy.setGmtCreate(instance.getGmtCreate());
        copy.setGmtModified(instance.getGmtModified());
        return copy;
    }

    private void releaseApacheEndpointIfUnused(InstanceVO existing, String currentEndpoint) {
        InstanceVendor vendor = existing.getVendor() == null ? InstanceVendor.APACHE : existing.getVendor();
        if (vendor != InstanceVendor.APACHE) {
            return;
        }
        releaseOldClientIfUnused(existing, currentEndpoint, null, existing.getId());
    }

    private void releaseApacheClientIfChanged(InstanceVO existing, InstanceVO saved) {
        InstanceVendor vendor = existing.getVendor() == null ? InstanceVendor.APACHE : existing.getVendor();
        if (vendor != InstanceVendor.APACHE) {
            return;
        }
        releaseOldClientIfUnused(existing, saved.getEndpoint(), saved.getAdminCredentialRef(), existing.getId());
    }

    private void releaseOldClientIfUnused(InstanceVO existing, String currentEndpoint,
                                          String currentCredentialRef, Long excludedInstanceId) {
        String oldEndpoint = normalizeEndpoint(existing.getEndpoint());
        String oldCredentialRef = normalizeCredentialRef(existing.getAdminCredentialRef());
        if (oldEndpoint == null || oldEndpoint.equals(normalizeEndpoint(currentEndpoint))
                && Objects.equals(oldCredentialRef, normalizeCredentialRef(currentCredentialRef))) {
            return;
        }
        List<InstanceVO> remaining = instanceRepository.findAll().stream()
                .filter(instance -> !excludedInstanceId.equals(instance.getId()))
                .toList();
        boolean endpointReferenced = remaining.stream()
                .anyMatch(instance -> oldEndpoint.equals(normalizeEndpoint(instance.getEndpoint())));
        if (!endpointReferenced) {
            adminFactory.release(oldEndpoint);
            return;
        }
        boolean identityReferenced = remaining.stream()
                .anyMatch(instance -> oldEndpoint.equals(normalizeEndpoint(instance.getEndpoint()))
                        && Objects.equals(oldCredentialRef, normalizeCredentialRef(instance.getAdminCredentialRef())));
        if (!identityReferenced) {
            adminFactory.release(oldEndpoint, oldCredentialRef);
        }
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
