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

import org.apache.rocketmq.studio.common.domain.enums.CertStatus;
import org.apache.rocketmq.studio.common.domain.enums.CertType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class K8sCertService {

    private static final int EXPIRING_THRESHOLD_DAYS = 30;

    private final K8sCertRepository k8sCertRepository;
    private final OperationAuditService operationAuditService;
    private final Clock clock;

    @Autowired
    public K8sCertService(K8sCertRepository k8sCertRepository, OperationAuditService operationAuditService) {
        this(k8sCertRepository, operationAuditService, Clock.systemDefaultZone());
    }

    K8sCertService(K8sCertRepository k8sCertRepository, OperationAuditService operationAuditService, Clock clock) {
        this.k8sCertRepository = k8sCertRepository;
        this.operationAuditService = operationAuditService;
        this.clock = clock;
    }

    public List<K8sCertVO> listCerts() {
        log.info("Listing all K8s certificates");
        LocalDateTime now = LocalDateTime.now(clock);
        return k8sCertRepository.findAll().stream()
                .map(cert -> refreshExpirationState(cert, now))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public K8sCertVO createCert(CreateCertDTO command) {
        requireCommand(command);
        log.info("Creating K8s certificate: {}", command.getName());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime notAfter = now.plusYears(1);

        K8sCertVO cert = K8sCertVO.builder()
                .name(command.getName())
                .namespace(command.getNamespace())
                .cluster(command.getCluster())
                .type(CertType.valueOf(command.getType()))
                .issuer(command.getIssuer())
                .notBefore(now)
                .notAfter(notAfter)
                .status(CertStatus.valid)
                .daysRemaining((int) ChronoUnit.DAYS.between(now, notAfter))
                .san(command.getSan())
                .build();
        cert.setId(UUID.randomUUID().toString());
        cert.setCreatedAt(now);
        cert.setUpdatedAt(now);

        K8sCertVO saved = k8sCertRepository.save(cert);
        auditCertificate("CREATE_K8S_CERTIFICATE", saved);
        log.info("K8s certificate created: {} (id={})", saved.getName(), saved.getId());
        return saved;
    }

    public K8sCertVO updateCert(UpdateCertDTO command) {
        requireCommand(command);
        log.info("Updating K8s certificate: {}", command.getId());
        K8sCertVO existing = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        K8sCertVO updated = copyOf(existing);
        String name = normalizeOptionalIdentity(command.getName(), "name");
        String namespace = normalizeOptionalIdentity(command.getNamespace(), "namespace");
        String cluster = normalizeOptionalIdentity(command.getCluster(), "cluster");
        String issuer = normalizeOptionalIdentity(command.getIssuer(), "issuer");
        if (name != null) {
            updated.setName(name);
        }
        if (namespace != null) {
            updated.setNamespace(namespace);
        }
        if (cluster != null) {
            updated.setCluster(cluster);
        }
        if (command.getType() != null) {
            updated.setType(CertType.valueOf(command.getType()));
        }
        if (issuer != null) {
            updated.setIssuer(issuer);
        }
        if (command.getSan() != null) {
            updated.setSan(command.getSan());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        updated.setUpdatedAt(now);

        K8sCertVO saved = k8sCertRepository.save(refreshExpirationState(updated, now));
        auditCertificate("UPDATE_K8S_CERTIFICATE", saved);
        log.info("K8s certificate updated: {} (id={})", saved.getName(), saved.getId());
        return saved;
    }

    public K8sCertVO renewCert(RenewCertDTO command) {
        requireCommand(command);
        log.info("Renewing K8s certificate: {}", command.getId());
        K8sCertVO existing = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime notAfter = now.plusYears(1);

        K8sCertVO renewed = copyOf(existing);
        renewed.setNotBefore(now);
        renewed.setNotAfter(notAfter);
        renewed.setStatus(CertStatus.valid);
        renewed.setDaysRemaining((int) ChronoUnit.DAYS.between(now, notAfter));
        renewed.setUpdatedAt(now);

        K8sCertVO saved = k8sCertRepository.save(renewed);
        auditCertificate("RENEW_K8S_CERTIFICATE", saved);
        log.info("K8s certificate renewed: {} (id={}), new expiry: {}", saved.getName(), saved.getId(), notAfter);
        return saved;
    }

    public void deleteCert(DeleteCertDTO command) {
        requireCommand(command);
        log.info("Deleting K8s certificate: {}", command.getId());
        k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));
        if (!k8sCertRepository.deleteById(command.getId())) {
            throw new BusinessException(404, "Certificate not found: " + command.getId());
        }
        recordAudit("DELETE_K8S_CERTIFICATE", "K8S_CERTIFICATE", command.getId(), null,
                null);
        log.info("K8s certificate deleted: {}", command.getId());
    }

    private void requireCommand(Object command) {
        if (command == null) {
            throw new BusinessException(400, "K8s certificate request is required");
        }
    }

    private String normalizeOptionalIdentity(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "Certificate " + field + " cannot be blank");
        }
        return normalized;
    }

    private K8sCertVO refreshExpirationState(K8sCertVO cert, LocalDateTime now) {
        K8sCertVO refreshed = copyOf(cert);
        LocalDateTime notAfter = refreshed.getNotAfter();
        if (notAfter == null) {
            return refreshed;
        }

        int daysRemaining = (int) ChronoUnit.DAYS.between(now, notAfter);
        refreshed.setDaysRemaining(daysRemaining);
        if (!notAfter.isAfter(now)) {
            refreshed.setStatus(CertStatus.expired);
        } else if (daysRemaining <= EXPIRING_THRESHOLD_DAYS) {
            refreshed.setStatus(CertStatus.expiring);
        } else {
            refreshed.setStatus(CertStatus.valid);
        }
        return refreshed;
    }

    private K8sCertVO copyOf(K8sCertVO cert) {
        K8sCertVO copy = K8sCertVO.builder()
                .name(cert.getName())
                .namespace(cert.getNamespace())
                .cluster(cert.getCluster())
                .type(cert.getType())
                .issuer(cert.getIssuer())
                .notBefore(cert.getNotBefore())
                .notAfter(cert.getNotAfter())
                .status(cert.getStatus())
                .daysRemaining(cert.getDaysRemaining())
                .san(cert.getSan())
                .build();
        copy.setId(cert.getId());
        copy.setCreatedAt(cert.getCreatedAt());
        copy.setUpdatedAt(cert.getUpdatedAt());
        return copy;
    }

    private void auditCertificate(String operation, K8sCertVO certificate) {
        recordAudit(operation, "K8S_CERTIFICATE", certificate.getId(), null,
                "name=" + certificate.getName() + ", namespace=" + certificate.getNamespace()
                        + ", cluster=" + certificate.getCluster());
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
