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
package org.apache.rocketmq.studio.cluster.k8s;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.enums.CertStatus;
import org.apache.rocketmq.studio.common.domain.enums.CertType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqK8sCertificate;
import org.apache.rocketmq.studio.persistence.mapper.RmqK8sCertificateMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MySQL-backed K8s certificate repository. The SAN list is stored as a JSON
 * array string.
 */
@RequiredArgsConstructor
@Repository
public class MybatisPlusK8sCertRepository implements K8sCertRepository {

    private final RmqK8sCertificateMapper certMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<K8sCertVO> findAll() {
        return certMapper.selectList(new QueryWrapper<RmqK8sCertificate>().orderByAsc("name")).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<K8sCertVO> findById(String id) {
        return Optional.ofNullable(certMapper.selectById(id)).map(this::toVO);
    }

    @Override
    @Transactional
    public K8sCertVO save(K8sCertVO cert) {
        RmqK8sCertificate entity = toEntity(cert);
        if (entity.getId() != null && certMapper.selectById(entity.getId()) != null) {
            certMapper.updateById(entity);
        } else {
            certMapper.insert(entity);
            cert.setId(entity.getId());
        }
        return cert;
    }

    @Override
    public void deleteById(String id) {
        certMapper.deleteById(id);
    }

    private K8sCertVO toVO(RmqK8sCertificate entity) {
        K8sCertVO vo = new K8sCertVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setNamespace(entity.getNamespace());
        vo.setCluster(entity.getCluster());
        vo.setType(parseCertType(entity.getId(), entity.getCertType()));
        vo.setIssuer(entity.getIssuer());
        vo.setNotBefore(entity.getNotBefore());
        vo.setNotAfter(entity.getNotAfter());
        vo.setStatus(parseCertStatus(entity.getId(), entity.getStatus()));
        vo.setDaysRemaining(entity.getDaysRemaining() == null ? 0 : entity.getDaysRemaining());
        vo.setSan(parseSan(entity.getId(), entity.getSan()));
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private RmqK8sCertificate toEntity(K8sCertVO cert) {
        RmqK8sCertificate entity = new RmqK8sCertificate();
        entity.setId(cert.getId());
        entity.setName(cert.getName());
        entity.setNamespace(cert.getNamespace());
        entity.setCluster(cert.getCluster());
        entity.setCertType(cert.getType() == null ? null : cert.getType().name());
        entity.setIssuer(cert.getIssuer());
        entity.setNotBefore(cert.getNotBefore());
        entity.setNotAfter(cert.getNotAfter());
        entity.setStatus(cert.getStatus() == null ? null : cert.getStatus().name());
        entity.setDaysRemaining(cert.getDaysRemaining());
        entity.setSan(writeSan(cert.getSan()));
        entity.setCreatedAt(cert.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private CertType parseCertType(String certificateId, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        for (CertType type : CertType.values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw invalidPersistedValue(certificateId, "type", value);
    }

    private CertStatus parseCertStatus(String certificateId, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return CertStatus.valueOf(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidPersistedValue(certificateId, "status", value);
        }
    }

    private List<String> parseSan(String certificateId, String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw invalidPersistedValue(certificateId, "SAN JSON", json);
        }
    }

    private BusinessException invalidPersistedValue(String certificateId, String field, String value) {
        return new BusinessException(500, "Invalid persisted certificate " + field
                + " for certificate " + certificateId + ": " + value);
    }

    private String writeSan(List<String> san) {
        if (san == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(san);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize certificate SAN values", exception);
        }
    }
}
