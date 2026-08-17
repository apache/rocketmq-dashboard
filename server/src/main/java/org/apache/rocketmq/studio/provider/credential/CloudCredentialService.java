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
package org.apache.rocketmq.studio.provider.credential;

import org.springframework.util.StringUtils;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.provider.alibaba.AliyunClientFactory;
import org.apache.rocketmq.studio.provider.tencent.TencentClientFactory;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class CloudCredentialService {

    private final CloudCredentialRepository credentialRepository;
    private final InstanceRepository instanceRepository;
    private final AliyunClientFactory aliyunClientFactory;
    private final TencentClientFactory tencentClientFactory;
    private final OperationAuditService operationAuditService;

    public List<CloudCredentialVO> listMasked() {
        log.info("Listing cloud credentials (masked)");
        return credentialRepository.findAll().stream()
                .map(this::maskAccessKey)
                .toList();
    }
    public PageResult<CloudCredentialVO> listMasked(InstanceVendor vendor, String search, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(400, "Invalid page or pageSize");
        PageResult<CloudCredentialVO> result = credentialRepository.findPage(vendor, search, page, pageSize);
        return PageResult.of(result.getItems().stream().map(this::maskAccessKey).toList(), result.getTotal(), page, pageSize);
    }

    public CloudCredentialVO create(CloudCredentialVO credential) {
        if (credential == null) {
            throw new BusinessException(400, "Cloud credential request is required");
        }
        if (!StringUtils.hasText(credential.getName())) {
            throw new BusinessException(400, "Cloud credential name is required");
        }
        if (credential.getVendor() == null || credential.getVendor() == org.apache.rocketmq.studio.common.domain.enums.InstanceVendor.APACHE) {
            throw new BusinessException(400, "Cloud credential vendor must be ALIYUN or TENCENT");
        }
        if (!StringUtils.hasText(credential.getAccessKey()) || !StringUtils.hasText(credential.getSecretKey())) {
            throw new BusinessException(400, "Cloud credential accessKey and secretKey are required");
        }
        credentialRepository.findByVendorAndAccessKey(credential.getVendor(), credential.getAccessKey())
                .ifPresent(existing -> {
                    throw new BusinessException(400,
                            "Cloud credential already exists for vendor " + credential.getVendor()
                                    + " and accessKey " + CredentialUtils.mask(credential.getAccessKey()));
                });
        log.info("Creating cloud credential name={}, vendor={}", credential.getName(), credential.getVendor());
        credential.setId(UUID.randomUUID().toString());
        credential.setCreatedAt(LocalDateTime.now());
        credential.setUpdatedAt(LocalDateTime.now());
        CloudCredentialVO saved = credentialRepository.save(credential);
        recordAudit("CREATE_CLOUD_CREDENTIAL", "CLOUD_CREDENTIAL", saved.getId(), null,
                credentialAuditDetail(saved));
        return maskAccessKey(saved);
    }

    public CloudCredentialVO update(UpdateCloudCredentialDTO request) {
        if (request == null || !StringUtils.hasText(request.getId())) {
            throw new BusinessException(400, "Cloud credential id is required");
        }
        log.info("Updating cloud credential id={}", request.getId());
        CloudCredentialVO existing = credentialRepository.findById(request.getId())
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + request.getId()));
        if (request.getName() != null && request.getName().isBlank()) {
            throw new BusinessException(400, "Cloud credential name cannot be blank");
        }
        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getSecretKey() != null && !request.getSecretKey().isBlank()) {
            existing.setSecretKey(request.getSecretKey());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        CloudCredentialVO saved = credentialRepository.save(existing);
        invalidateCloudClients(saved);
        recordAudit("UPDATE_CLOUD_CREDENTIAL", "CLOUD_CREDENTIAL", saved.getId(), null,
                credentialAuditDetail(saved));
        return maskAccessKey(saved);
    }

    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "Cloud credential id is required");
        }
        log.info("Deleting cloud credential id={}", id);
        CloudCredentialVO existing = credentialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + id));
        if (instanceRepository.existsByCredentialId(id)) {
            throw new BusinessException(400, "Cloud credential is referenced by existing instances");
        }
        if (!credentialRepository.deleteById(id)) {
            throw new BusinessException(404, "Cloud credential not found: " + id);
        }
        invalidateCloudClients(existing);
        recordAudit("DELETE_CLOUD_CREDENTIAL", "CLOUD_CREDENTIAL", id, null,
                credentialAuditDetail(existing));
    }

    public CloudCredentialVO reveal(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "Cloud credential id is required");
        }
        return credentialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + id));
    }

    private CloudCredentialVO maskAccessKey(CloudCredentialVO credential) {
        CloudCredentialVO masked = new CloudCredentialVO();
        masked.setId(credential.getId());
        masked.setName(credential.getName());
        masked.setVendor(credential.getVendor());
        masked.setAccessKey(CredentialUtils.mask(credential.getAccessKey()));
        masked.setSecretKey(null);
        masked.setRemark(credential.getRemark());
        masked.setCreatedAt(credential.getCreatedAt());
        masked.setUpdatedAt(credential.getUpdatedAt());
        return masked;
    }

    private String credentialAuditDetail(CloudCredentialVO credential) {
        return "name=" + credential.getName() + ", vendor=" + credential.getVendor();
    }

    private void invalidateCloudClients(CloudCredentialVO credential) {
        if (credential.getVendor() == org.apache.rocketmq.studio.common.domain.enums.InstanceVendor.ALIYUN) {
            aliyunClientFactory.invalidateCredential(credential.getId());
        } else if (credential.getVendor() == org.apache.rocketmq.studio.common.domain.enums.InstanceVendor.TENCENT) {
            tencentClientFactory.invalidateCredential(credential.getId());
        }
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
