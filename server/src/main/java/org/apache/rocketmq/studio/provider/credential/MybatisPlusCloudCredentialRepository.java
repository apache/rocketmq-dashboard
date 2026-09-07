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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqCloudCredential;
import org.apache.rocketmq.studio.persistence.mapper.RmqCloudCredentialMapper;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MySQL-backed cloud credential repository. Secret keys are stored base64-encoded in
 * {@code rmq_cloud_credential.secret_key} and decoded when read; plain text is never persisted.
 */
@RequiredArgsConstructor
@Repository
public class MybatisPlusCloudCredentialRepository implements CloudCredentialRepository {

    private final RmqCloudCredentialMapper credentialMapper;

    @Override
    public List<CloudCredentialVO> findAll() {
        return credentialMapper.selectList(
                        new QueryWrapper<RmqCloudCredential>().orderByAsc("id")).stream()
                .map(MybatisPlusCloudCredentialRepository::toVO)
                .collect(Collectors.toList());
    }
    @Override
    public PageResult<CloudCredentialVO> findPage(InstanceVendor vendor, String search, int page, int pageSize) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        QueryWrapper<RmqCloudCredential> q = new QueryWrapper<RmqCloudCredential>()
                .eq(vendor != null, "vendor", vendor == null ? null : vendor.name())
                .like(normalizedSearch != null, "name", normalizedSearch)
                .orderByDesc("gmt_modified", "id");
        Page<RmqCloudCredential> result = credentialMapper.selectPage(new Page<>(page, pageSize), q);
        return PageResult.of(result.getRecords().stream()
                        .map(MybatisPlusCloudCredentialRepository::toVO)
                        .toList(),
                result.getTotal(), page, pageSize);
    }

    @Override
    public Optional<CloudCredentialVO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(credentialMapper.selectById(id))
                .map(MybatisPlusCloudCredentialRepository::toVO);
    }

    @Override
    public Optional<CloudCredentialVO> findByVendorAndAccessKey(InstanceVendor vendor, String accessKey) {
        RmqCloudCredential entity = credentialMapper.selectOne(
                new QueryWrapper<RmqCloudCredential>()
                        .eq("vendor", vendor.name())
                        .eq("access_key", accessKey)
                        .last("LIMIT 1"));
        return Optional.ofNullable(entity).map(MybatisPlusCloudCredentialRepository::toVO);
    }

    @Override
    public CloudCredentialVO save(CloudCredentialVO credential) {
        RmqCloudCredential entity = toEntity(credential);
        if (entity.getId() != null) {
            if (credentialMapper.updateById(entity) == 0) {
                throw new BusinessException(409,
                        "Cloud credential update was not applied: " + entity.getId());
            }
        } else {
            credentialMapper.insert(entity);
            credential.setId(entity.getId());
        }
        return credential;
    }

    @Override
    public boolean replace(CloudCredentialVO credential) {
        return credential.getId() != null && credentialMapper.updateById(toEntity(credential)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && credentialMapper.deleteById(id) > 0;
    }

    // ── Mapping ────────────────────────────────────────────────────

    private static CloudCredentialVO toVO(RmqCloudCredential entity) {
        CloudCredentialVO vo = new CloudCredentialVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setVendor(parseVendor(entity.getId(), entity.getVendor()));
        vo.setAccessKey(entity.getAccessKey());
        vo.setSecretKey(CredentialUtils.decodeBase64(entity.getSecretKey()));
        vo.setRemark(entity.getRemark());
        vo.setGmtCreate(entity.getGmtCreate());
        vo.setGmtModified(entity.getGmtModified());
        return vo;
    }

    private static RmqCloudCredential toEntity(CloudCredentialVO vo) {
        RmqCloudCredential entity = new RmqCloudCredential();
        entity.setId(vo.getId());
        entity.setName(vo.getName());
        entity.setVendor(vo.getVendor() == null ? null : vo.getVendor().name());
        entity.setAccessKey(vo.getAccessKey());
        entity.setSecretKey(CredentialUtils.encodeBase64(vo.getSecretKey()));
        entity.setRemark(vo.getRemark());
        entity.setGmtCreate(vo.getGmtCreate());
        entity.setGmtModified(vo.getGmtModified() == null ? LocalDateTime.now() : vo.getGmtModified());
        return entity;
    }

    private static InstanceVendor parseVendor(Long credentialId, String vendor) {
        try {
            return InstanceVendor.valueOf(vendor);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(500, "Invalid persisted cloud credential vendor for credential "
                    + credentialId + ": " + vendor);
        }
    }
}
