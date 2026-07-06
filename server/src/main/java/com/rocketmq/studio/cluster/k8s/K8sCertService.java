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
package com.rocketmq.studio.cluster.k8s;

import com.rocketmq.studio.common.domain.enums.CertStatus;
import com.rocketmq.studio.common.domain.enums.CertType;
import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class K8sCertService {

    private final K8sCertRepository k8sCertRepository;

    public List<K8sCertVO> listCerts() {
        log.info("Listing all K8s certificates");
        return k8sCertRepository.findAll();
    }

    public K8sCertVO createCert(CreateCertDTO command) {
        log.info("Creating K8s certificate: {}", command.getName());

        LocalDateTime now = LocalDateTime.now();
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
        log.info("K8s certificate created: {} (id={})", saved.getName(), saved.getId());
        return saved;
    }

    public K8sCertVO updateCert(UpdateCertDTO command) {
        log.info("Updating K8s certificate: {}", command.getId());
        K8sCertVO cert = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        if (command.getName() != null) {
            cert.setName(command.getName());
        }
        if (command.getNamespace() != null) {
            cert.setNamespace(command.getNamespace());
        }
        if (command.getCluster() != null) {
            cert.setCluster(command.getCluster());
        }
        if (command.getType() != null) {
            cert.setType(CertType.valueOf(command.getType()));
        }
        if (command.getIssuer() != null) {
            cert.setIssuer(command.getIssuer());
        }
        if (command.getSan() != null) {
            cert.setSan(command.getSan());
        }
        cert.setUpdatedAt(LocalDateTime.now());

        K8sCertVO saved = k8sCertRepository.save(cert);
        log.info("K8s certificate updated: {} (id={})", saved.getName(), saved.getId());
        return saved;
    }

    public K8sCertVO renewCert(RenewCertDTO command) {
        log.info("Renewing K8s certificate: {}", command.getId());
        K8sCertVO cert = k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime notAfter = now.plusYears(1);

        cert.setNotBefore(now);
        cert.setNotAfter(notAfter);
        cert.setStatus(CertStatus.valid);
        cert.setDaysRemaining((int) ChronoUnit.DAYS.between(now, notAfter));
        cert.setUpdatedAt(now);

        K8sCertVO saved = k8sCertRepository.save(cert);
        log.info("K8s certificate renewed: {} (id={}), new expiry: {}", saved.getName(), saved.getId(), notAfter);
        return saved;
    }

    public void deleteCert(DeleteCertDTO command) {
        log.info("Deleting K8s certificate: {}", command.getId());
        k8sCertRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Certificate not found: " + command.getId()));
        k8sCertRepository.deleteById(command.getId());
        log.info("K8s certificate deleted: {}", command.getId());
    }
}
