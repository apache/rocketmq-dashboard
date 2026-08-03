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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class K8sCertServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2025-07-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private K8sCertRepository k8sCertRepository;

    private K8sCertService k8sCertService;

    private K8sCertVO sampleCert;

    @BeforeEach
    void setUp() {
        k8sCertService = new K8sCertService(k8sCertRepository, CLOCK);
        sampleCert = K8sCertVO.builder()
                .name("rocketmq-tls")
                .namespace("mq-system")
                .cluster("prod-cluster")
                .type(CertType.TLS)
                .issuer("letsencrypt")
                .notBefore(LocalDateTime.of(2025, 1, 1, 0, 0))
                .notAfter(LocalDateTime.of(2026, 1, 1, 0, 0))
                .status(CertStatus.valid)
                .daysRemaining(180)
                .san(Arrays.asList("rocketmq.example.com", "*.rocketmq.example.com"))
                .build();
        sampleCert.setId("cert-1");
        sampleCert.setCreatedAt(LocalDateTime.of(2024, 12, 1, 0, 0));
        sampleCert.setUpdatedAt(LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    @Test
    void listCertsShouldReturnAllCerts() {
        K8sCertVO secondCert = K8sCertVO.builder()
                .name("broker-mtls")
                .type(CertType.mTLS)
                .status(CertStatus.expiring)
                .build();
        secondCert.setId("cert-2");

        when(k8sCertRepository.findAll()).thenReturn(Arrays.asList(sampleCert, secondCert));

        List<K8sCertVO> result = k8sCertService.listCerts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("rocketmq-tls");
        assertThat(result.get(0).getType()).isEqualTo(CertType.TLS);
        assertThat(result.get(1).getName()).isEqualTo("broker-mtls");
        assertThat(result.get(1).getType()).isEqualTo(CertType.mTLS);
        verify(k8sCertRepository).findAll();
    }

    @Test
    void listCertsShouldReturnEmptyListWhenNoCerts() {
        when(k8sCertRepository.findAll()).thenReturn(Collections.emptyList());

        List<K8sCertVO> result = k8sCertService.listCerts();

        assertThat(result).isEmpty();
    }

    @Test
    void listCertsShouldRefreshTimeDerivedExpiryFields() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        sampleCert.setNotAfter(now.minusDays(1));
        sampleCert.setStatus(CertStatus.valid);
        sampleCert.setDaysRemaining(180);
        K8sCertVO expiresNowCert = copyWithExpiry("cert-expires-now", now,
                CertStatus.valid, 180);
        K8sCertVO expiringCert = copyWithExpiry("cert-expiring", now.plusDays(30),
                CertStatus.expired, -1);
        K8sCertVO validCert = copyWithExpiry("cert-valid", now.plusDays(31),
                CertStatus.expired, -1);
        when(k8sCertRepository.findAll())
                .thenReturn(List.of(sampleCert, expiresNowCert, expiringCert, validCert));

        List<K8sCertVO> result = k8sCertService.listCerts();

        assertThat(result).extracting(K8sCertVO::getStatus)
                .containsExactly(CertStatus.expired, CertStatus.expired,
                        CertStatus.expiring, CertStatus.valid);
        assertThat(result).extracting(K8sCertVO::getDaysRemaining)
                .containsExactly(-1, 0, 30, 31);
        assertThat(result.get(0)).isNotSameAs(sampleCert);
        assertThat(sampleCert.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(sampleCert.getDaysRemaining()).isEqualTo(180);
        assertThat(expiringCert.getStatus()).isEqualTo(CertStatus.expired);
        assertThat(expiringCert.getDaysRemaining()).isEqualTo(-1);
    }

    @Test
    void createCertShouldCreateAndSaveCert() {
        CreateCertDTO command = CreateCertDTO.builder()
                .name("new-tls-cert")
                .namespace("default")
                .cluster("test-cluster")
                .type("TLS")
                .issuer("vault")
                .san(List.of("svc.example.com"))
                .build();

        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> {
            K8sCertVO cert = invocation.getArgument(0);
            if (cert.getId() == null) {
                cert.setId("generated-id");
            }
            return cert;
        });

        K8sCertVO result = k8sCertService.createCert(command);
        LocalDateTime now = LocalDateTime.now(CLOCK);

        assertThat(result.getName()).isEqualTo("new-tls-cert");
        assertThat(result.getNamespace()).isEqualTo("default");
        assertThat(result.getCluster()).isEqualTo("test-cluster");
        assertThat(result.getType()).isEqualTo(CertType.TLS);
        assertThat(result.getIssuer()).isEqualTo("vault");
        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getSan()).containsExactly("svc.example.com");
        assertThat(result.getNotBefore()).isEqualTo(now);
        assertThat(result.getNotAfter()).isEqualTo(now.plusYears(1));
        assertThat(result.getDaysRemaining()).isEqualTo(365);
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getUpdatedAt()).isEqualTo(now);
        verify(k8sCertRepository).save(any(K8sCertVO.class));
    }

    @Test
    void certWriteOperationsShouldRejectNullCommand() {
        assertThatThrownBy(() -> k8sCertService.createCert(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("K8s certificate request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> k8sCertService.updateCert(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("K8s certificate request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> k8sCertService.renewCert(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("K8s certificate request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> k8sCertService.deleteCert(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("K8s certificate request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(k8sCertRepository);
    }

    @Test
    void createCertShouldSetCorrectValidityPeriod() {
        CreateCertDTO command = CreateCertDTO.builder()
                .name("validity-test")
                .namespace("default")
                .cluster("test-cluster")
                .type("TLS")
                .issuer("test-issuer")
                .build();

        ArgumentCaptor<K8sCertVO> captor = ArgumentCaptor.forClass(K8sCertVO.class);
        when(k8sCertRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        k8sCertService.createCert(command);

        K8sCertVO saved = captor.getValue();
        LocalDateTime now = LocalDateTime.now(CLOCK);
        assertThat(saved.getNotBefore()).isEqualTo(now);
        assertThat(saved.getNotAfter()).isEqualTo(now.plusYears(1));
        assertThat(saved.getDaysRemaining()).isEqualTo(365);
    }

    @Test
    void updateCertShouldUpdateFieldsWhenFound() {
        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCertDTO command = UpdateCertDTO.builder()
                .id("cert-1")
                .name("updated-name")
                .namespace("new-namespace")
                .cluster("new-cluster")
                .type("mTLS")
                .issuer("new-issuer")
                .san(List.of("new.example.com"))
                .build();

        K8sCertVO result = k8sCertService.updateCert(command);

        assertThat(result.getName()).isEqualTo("updated-name");
        assertThat(result.getNamespace()).isEqualTo("new-namespace");
        assertThat(result.getCluster()).isEqualTo("new-cluster");
        assertThat(result.getType()).isEqualTo(CertType.mTLS);
        assertThat(result.getIssuer()).isEqualTo("new-issuer");
        assertThat(result.getSan()).containsExactly("new.example.com");
        assertThat(result.getId()).isEqualTo("cert-1");
        assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 12, 1, 0, 0));
        assertThat(result.getUpdatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(result).isNotSameAs(sampleCert);
        assertThat(sampleCert.getName()).isEqualTo("rocketmq-tls");
        assertThat(sampleCert.getType()).isEqualTo(CertType.TLS);
        assertThat(sampleCert.getUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
        verify(k8sCertRepository).save(any(K8sCertVO.class));
    }

    @Test
    void updateCertShouldRefreshTimeDerivedExpiryFields() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        sampleCert.setNotAfter(now.minusDays(1));
        sampleCert.setStatus(CertStatus.valid);
        sampleCert.setDaysRemaining(180);
        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateCertDTO command = UpdateCertDTO.builder()
                .id("cert-1")
                .name("updated-expired-cert")
                .build();

        K8sCertVO result = k8sCertService.updateCert(command);

        assertThat(result.getStatus()).isEqualTo(CertStatus.expired);
        assertThat(result.getDaysRemaining()).isEqualTo(-1);
        assertThat(sampleCert.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(sampleCert.getDaysRemaining()).isEqualTo(180);
        verify(k8sCertRepository).save(result);
    }

    @Test
    void updateCertShouldPreserveExistingFieldsWhenCommandFieldsAreNull() {
        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCertDTO command = UpdateCertDTO.builder()
                .id("cert-1")
                .name("only-name-changed")
                .build();

        K8sCertVO result = k8sCertService.updateCert(command);

        assertThat(result.getName()).isEqualTo("only-name-changed");
        assertThat(result.getNamespace()).isEqualTo("mq-system");
        assertThat(result.getCluster()).isEqualTo("prod-cluster");
        assertThat(result.getType()).isEqualTo(CertType.TLS);
        assertThat(result.getIssuer()).isEqualTo("letsencrypt");
    }

    @Test
    void updateCertShouldThrowWhenNotFound() {
        when(k8sCertRepository.findById("nonexistent")).thenReturn(Optional.empty());

        UpdateCertDTO command = UpdateCertDTO.builder()
                .id("nonexistent")
                .name("wont-work")
                .build();

        assertThatThrownBy(() -> k8sCertService.updateCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Certificate not found: nonexistent")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void updateCertShouldNotMutateStoredCertWhenSaveFails() {
        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenThrow(new IllegalStateException("save failed"));
        UpdateCertDTO command = UpdateCertDTO.builder()
                .id("cert-1")
                .name("should-not-persist")
                .type("mTLS")
                .build();

        assertThatThrownBy(() -> k8sCertService.updateCert(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(sampleCert.getName()).isEqualTo("rocketmq-tls");
        assertThat(sampleCert.getType()).isEqualTo(CertType.TLS);
        assertThat(sampleCert.getUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    @Test
    void renewCertShouldRenewCertValidity() {
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setDaysRemaining(0);
        LocalDateTime originalNotBefore = sampleCert.getNotBefore();
        LocalDateTime originalNotAfter = sampleCert.getNotAfter();

        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RenewCertDTO command = RenewCertDTO.builder().id("cert-1").build();

        K8sCertVO result = k8sCertService.renewCert(command);
        LocalDateTime now = LocalDateTime.now(CLOCK);

        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getDaysRemaining()).isEqualTo(365);
        assertThat(result.getNotBefore()).isEqualTo(now);
        assertThat(result.getNotAfter()).isEqualTo(now.plusYears(1));
        assertThat(result.getUpdatedAt()).isEqualTo(now);
        assertThat(result.getId()).isEqualTo("cert-1");
        assertThat(result.getCreatedAt()).isEqualTo(sampleCert.getCreatedAt());
        assertThat(result).isNotSameAs(sampleCert);
        assertThat(sampleCert.getStatus()).isEqualTo(CertStatus.expired);
        assertThat(sampleCert.getDaysRemaining()).isZero();
        assertThat(sampleCert.getNotBefore()).isEqualTo(originalNotBefore);
        assertThat(sampleCert.getNotAfter()).isEqualTo(originalNotAfter);
        verify(k8sCertRepository).save(any(K8sCertVO.class));
    }

    @Test
    void renewCertShouldNotMutateStoredCertWhenSaveFails() {
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setDaysRemaining(0);
        LocalDateTime originalNotBefore = sampleCert.getNotBefore();
        LocalDateTime originalNotAfter = sampleCert.getNotAfter();

        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenThrow(new IllegalStateException("save failed"));

        RenewCertDTO command = RenewCertDTO.builder().id("cert-1").build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(sampleCert.getStatus()).isEqualTo(CertStatus.expired);
        assertThat(sampleCert.getDaysRemaining()).isZero();
        assertThat(sampleCert.getNotBefore()).isEqualTo(originalNotBefore);
        assertThat(sampleCert.getNotAfter()).isEqualTo(originalNotAfter);
        assertThat(sampleCert.getUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    @Test
    void renewCertShouldThrowWhenNotFound() {
        when(k8sCertRepository.findById("nonexistent")).thenReturn(Optional.empty());

        RenewCertDTO command = RenewCertDTO.builder().id("nonexistent").build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Certificate not found: nonexistent");
    }

    @Test
    void deleteCertShouldDeleteWhenFound() {
        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));

        DeleteCertDTO command = DeleteCertDTO.builder().id("cert-1").build();

        k8sCertService.deleteCert(command);

        verify(k8sCertRepository).deleteById("cert-1");
    }

    @Test
    void deleteCertShouldThrowWhenNotFound() {
        when(k8sCertRepository.findById("nonexistent")).thenReturn(Optional.empty());

        DeleteCertDTO command = DeleteCertDTO.builder().id("nonexistent").build();

        assertThatThrownBy(() -> k8sCertService.deleteCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Certificate not found: nonexistent");
    }

    private K8sCertVO copyWithExpiry(String id, LocalDateTime notAfter, CertStatus status,
                                     int daysRemaining) {
        K8sCertVO cert = K8sCertVO.builder()
                .name(sampleCert.getName())
                .namespace(sampleCert.getNamespace())
                .cluster(sampleCert.getCluster())
                .type(sampleCert.getType())
                .issuer(sampleCert.getIssuer())
                .notBefore(sampleCert.getNotBefore())
                .notAfter(notAfter)
                .status(status)
                .daysRemaining(daysRemaining)
                .san(sampleCert.getSan())
                .build();
        cert.setId(id);
        cert.setCreatedAt(sampleCert.getCreatedAt());
        cert.setUpdatedAt(sampleCert.getUpdatedAt());
        return cert;
    }
}
