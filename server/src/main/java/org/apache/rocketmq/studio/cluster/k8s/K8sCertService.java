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

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
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
        log.info("Creating K8s certificate: {}", command.getK8sId());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime notBefore = now;
        LocalDateTime notAfter = now.plusYears(1);
        String issuer = command.getIssuer();
        List<String> san = command.getSan();
        if (command.getCertPem() != null && !command.getCertPem().isBlank()) {
            X509Certificate parsed = parseCertificate(command.getCertPem());
            notBefore = LocalDateTime.ofInstant(parsed.getNotBefore().toInstant(), ZoneId.systemDefault());
            notAfter = LocalDateTime.ofInstant(parsed.getNotAfter().toInstant(), ZoneId.systemDefault());
            issuer = parsed.getIssuerX500Principal().getName();
            san = extractSubjectAlternativeNames(parsed);
        }

        K8sCertVO cert = K8sCertVO.builder()
                .k8sId(command.getK8sId())
                .cluster(command.getCluster())
                .type(CertType.valueOf(command.getType()))
                .issuer(issuer)
                .notBefore(notBefore)
                .notAfter(notAfter)
                .status(CertStatus.valid)
                .san(san)
                .certPem(command.getCertPem())
                .keyPem(command.getKeyPem())
                .build();
        cert.setGmtCreate(now);
        cert.setGmtModified(now);

        K8sCertVO saved = k8sCertRepository.save(refreshExpirationState(cert, now));
        auditCertificate("CREATE_K8S_CERTIFICATE", saved);
        log.info("K8s certificate created: {} (id={})", saved.getK8sId(), saved.getId());
        return saved;
    }

    public K8sCertVO updateCert(UpdateCertDTO command) {
        requireCommand(command);
        log.info("Updating K8s certificate: {}", command.getId());
        K8sCertVO existing = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        K8sCertVO updated = copyOf(existing);
        String k8sId = normalizeOptionalIdentity(command.getK8sId(), "k8sId");
        String cluster = normalizeOptionalIdentity(command.getCluster(), "cluster");
        String issuer = normalizeOptionalIdentity(command.getIssuer(), "issuer");
        if (k8sId != null) {
            updated.setK8sId(k8sId);
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
        updated.setGmtModified(now);

        K8sCertVO saved = k8sCertRepository.save(refreshExpirationState(updated, now));
        auditCertificate("UPDATE_K8S_CERTIFICATE", saved);
        log.info("K8s certificate updated: {} (id={})", saved.getK8sId(), saved.getId());
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
        renewed.setGmtModified(now);

        K8sCertVO saved = k8sCertRepository.save(renewed);
        auditCertificate("RENEW_K8S_CERTIFICATE", saved);
        log.info("K8s certificate renewed: {} (id={}), new expiry: {}", saved.getK8sId(), saved.getId(), notAfter);
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
        recordAudit("DELETE_K8S_CERTIFICATE", "K8S_CERTIFICATE", String.valueOf(command.getId()), null,
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
                .k8sId(cert.getK8sId())
                .cluster(cert.getCluster())
                .type(cert.getType())
                .issuer(cert.getIssuer())
                .notBefore(cert.getNotBefore())
                .notAfter(cert.getNotAfter())
                .status(cert.getStatus())
                .daysRemaining(cert.getDaysRemaining())
                .san(cert.getSan())
                .certPem(cert.getCertPem())
                .keyPem(cert.getKeyPem())
                .build();
        copy.setId(cert.getId());
        copy.setGmtCreate(cert.getGmtCreate());
        copy.setGmtModified(cert.getGmtModified());
        return copy;
    }

    private void auditCertificate(String operation, K8sCertVO certificate) {
        recordAudit(operation, "K8S_CERTIFICATE", String.valueOf(certificate.getId()), null,
                "k8sId=" + certificate.getK8sId() + ", cluster=" + certificate.getCluster());
    }

    private X509Certificate parseCertificate(String certPem) {
        String base64 = certPem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch (Exception exception) {
            throw new BusinessException(400, "Invalid certificate content, expected a PEM encoded X.509 certificate");
        }
    }

    private List<String> extractSubjectAlternativeNames(X509Certificate certificate) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) {
                return List.of();
            }
            return names.stream()
                    .filter(entry -> entry.size() >= 2 && entry.get(1) instanceof String)
                    .map(entry -> (String) entry.get(1))
                    .collect(Collectors.toList());
        } catch (CertificateParsingException exception) {
            log.warn("Failed to parse SANs of certificate {}: {}", certificate.getSubjectX500Principal(),
                    exception.getMessage());
            return List.of();
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
