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
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class K8sCertServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2025-07-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private K8sCertRepository k8sCertRepository;

    @Mock
    private OperationAuditService operationAuditService;

    private K8sCertService k8sCertService;

    private K8sCertVO sampleCert;

    @BeforeEach
    void setUp() {
        k8sCertService = new K8sCertService(k8sCertRepository, operationAuditService, CLOCK);
        sampleCert = K8sCertVO.builder()
                .k8sId("rocketmq-tls")
                .cluster("prod-cluster")
                .type(CertType.TLS)
                .issuer("letsencrypt")
                .notBefore(LocalDateTime.of(2025, 1, 1, 0, 0))
                .notAfter(LocalDateTime.of(2026, 1, 1, 0, 0))
                .status(CertStatus.valid)
                .daysRemaining(180)
                .san(Arrays.asList("rocketmq.example.com", "*.rocketmq.example.com"))
                .build();
        sampleCert.setId(1L);
        sampleCert.setGmtCreate(LocalDateTime.of(2024, 12, 1, 0, 0));
        sampleCert.setGmtModified(LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    @Test
    void listCertsShouldReturnAllCerts() {
        K8sCertVO secondCert = K8sCertVO.builder()
                .k8sId("broker-mtls")
                .type(CertType.mTLS)
                .status(CertStatus.expiring)
                .build();
        secondCert.setId(2L);

        when(k8sCertRepository.findAll()).thenReturn(Arrays.asList(sampleCert, secondCert));

        List<K8sCertVO> result = k8sCertService.listCerts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getK8sId()).isEqualTo("rocketmq-tls");
        assertThat(result.get(0).getType()).isEqualTo(CertType.TLS);
        assertThat(result.get(1).getK8sId()).isEqualTo("broker-mtls");
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
        K8sCertVO expiresNowCert = copyWithExpiry(3L, now,
                CertStatus.valid, 180);
        K8sCertVO expiringCert = copyWithExpiry(4L, now.plusDays(30),
                CertStatus.expired, -1);
        K8sCertVO validCert = copyWithExpiry(5L, now.plusDays(31),
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
                .k8sId("new-tls-cert")
                .cluster("test-cluster")
                .type("TLS")
                .issuer("vault")
                .san(List.of("svc.example.com"))
                .build();

        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> {
            K8sCertVO cert = invocation.getArgument(0);
            if (cert.getId() == null) {
                cert.setId(100L);
            }
            return cert;
        });

        K8sCertVO result = k8sCertService.createCert(command);
        LocalDateTime now = LocalDateTime.now(CLOCK);

        assertThat(result.getK8sId()).isEqualTo("new-tls-cert");
        assertThat(result.getCluster()).isEqualTo("test-cluster");
        assertThat(result.getType()).isEqualTo(CertType.TLS);
        assertThat(result.getIssuer()).isEqualTo("vault");
        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getSan()).containsExactly("svc.example.com");
        assertThat(result.getNotBefore()).isEqualTo(now);
        assertThat(result.getNotAfter()).isEqualTo(now.plusYears(1));
        assertThat(result.getDaysRemaining()).isEqualTo(365);
        assertThat(result.getGmtCreate()).isEqualTo(now);
        assertThat(result.getGmtModified()).isEqualTo(now);
        verify(k8sCertRepository).save(any(K8sCertVO.class));
        verify(operationAuditService).record(eq("CREATE_K8S_CERTIFICATE"), eq("K8S_CERTIFICATE"),
                eq("100"), eq(null), eq("k8sId=new-tls-cert, cluster=test-cluster"),
                eq("SUCCESS"), eq(null));
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
    void createCertShouldRejectInvalidTypeBeforeSave() {
        CreateCertDTO command = CreateCertDTO.builder()
                .k8sId("bad-cert")
                .cluster("test-cluster")
                .type("INVALID")
                .issuer("test-issuer")
                .build();

        assertThatThrownBy(() -> k8sCertService.createCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid certificate type: INVALID. Valid types: TLS, mTLS, ServiceAccount")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(k8sCertRepository, never()).save(any());
        verify(operationAuditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createCertShouldRejectMissingTypeBeforeSave() {
        CreateCertDTO command = CreateCertDTO.builder()
                .k8sId("bad-cert")
                .cluster("test-cluster")
                .issuer("test-issuer")
                .build();

        assertThatThrownBy(() -> k8sCertService.createCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("type is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(k8sCertRepository, operationAuditService);
    }

    @Test
    void updateCertShouldRejectInvalidTypeBeforeSave() {
        UpdateCertDTO command = UpdateCertDTO.builder()
                .id(1L)
                .type("INVALID")
                .build();

        assertThatThrownBy(() -> k8sCertService.updateCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid certificate type: INVALID. Valid types: TLS, mTLS, ServiceAccount")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(k8sCertRepository, operationAuditService);
    }

    @Test
    void createCertShouldSetCorrectValidityPeriod() {
        CreateCertDTO command = CreateCertDTO.builder()
                .k8sId("validity-test")
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
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCertDTO command = UpdateCertDTO.builder()
                .id(1L)
                .k8sId("updated-name")
                .cluster("new-cluster")
                .type("mTLS")
                .issuer("new-issuer")
                .san(List.of("new.example.com"))
                .build();

        K8sCertVO result = k8sCertService.updateCert(command);

        assertThat(result.getK8sId()).isEqualTo("updated-name");
        assertThat(result.getCluster()).isEqualTo("new-cluster");
        assertThat(result.getType()).isEqualTo(CertType.mTLS);
        assertThat(result.getIssuer()).isEqualTo("new-issuer");
        assertThat(result.getSan()).containsExactly("new.example.com");
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getGmtCreate()).isEqualTo(LocalDateTime.of(2024, 12, 1, 0, 0));
        assertThat(result.getGmtModified()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(result).isNotSameAs(sampleCert);
        assertThat(sampleCert.getK8sId()).isEqualTo("rocketmq-tls");
        assertThat(sampleCert.getType()).isEqualTo(CertType.TLS);
        assertThat(sampleCert.getGmtModified()).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
        verify(k8sCertRepository).save(any(K8sCertVO.class));
        verify(operationAuditService).record(eq("UPDATE_K8S_CERTIFICATE"), eq("K8S_CERTIFICATE"),
                eq("1"), eq(null), eq("k8sId=updated-name, cluster=new-cluster"),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void updateCertShouldRefreshTimeDerivedExpiryFields() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        sampleCert.setNotAfter(now.minusDays(1));
        sampleCert.setStatus(CertStatus.valid);
        sampleCert.setDaysRemaining(180);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateCertDTO command = UpdateCertDTO.builder()
                .id(1L)
                .k8sId("updated-expired-cert")
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
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCertDTO command = UpdateCertDTO.builder()
                .id(1L)
                .k8sId("only-name-changed")
                .build();

        K8sCertVO result = k8sCertService.updateCert(command);

        assertThat(result.getK8sId()).isEqualTo("only-name-changed");
        assertThat(result.getCluster()).isEqualTo("prod-cluster");
        assertThat(result.getType()).isEqualTo(CertType.TLS);
        assertThat(result.getIssuer()).isEqualTo("letsencrypt");
    }

    @Test
    void updateCertShouldRejectBlankIdentityFields() {
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        List<Map.Entry<String, Consumer<UpdateCertDTO>>> invalidUpdates = List.of(
                Map.entry("k8sId", command -> command.setK8sId(" ")),
                Map.entry("cluster", command -> command.setCluster("\n")),
                Map.entry("issuer", command -> command.setIssuer("  ")));

        for (Map.Entry<String, Consumer<UpdateCertDTO>> invalidUpdate : invalidUpdates) {
            UpdateCertDTO command = UpdateCertDTO.builder().id(1L).build();
            invalidUpdate.getValue().accept(command);

            assertThatThrownBy(() -> k8sCertService.updateCert(command))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Certificate " + invalidUpdate.getKey() + " cannot be blank")
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        }

        verify(k8sCertRepository, never()).save(any(K8sCertVO.class));
    }

    @Test
    void updateCertShouldThrowWhenNotFound() {
        when(k8sCertRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateCertDTO command = UpdateCertDTO.builder()
                .id(999L)
                .k8sId("wont-work")
                .build();

        assertThatThrownBy(() -> k8sCertService.updateCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Certificate not found: 999")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void updateCertShouldNotMutateStoredCertWhenSaveFails() {
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenThrow(new IllegalStateException("save failed"));
        UpdateCertDTO command = UpdateCertDTO.builder()
                .id(1L)
                .k8sId("should-not-persist")
                .type("mTLS")
                .build();

        assertThatThrownBy(() -> k8sCertService.updateCert(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(sampleCert.getK8sId()).isEqualTo("rocketmq-tls");
        assertThat(sampleCert.getType()).isEqualTo(CertType.TLS);
        assertThat(sampleCert.getGmtModified()).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    @Test
    void renewCertShouldRequireReplacementCertificateMaterial() {
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));

        RenewCertDTO command = RenewCertDTO.builder().id(1L).build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("certPem is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verify(k8sCertRepository, never()).save(any(K8sCertVO.class));
    }

    @Test
    void renewCertShouldReplaceStoredMaterialWithParsedCertificate() {
        sampleCert.setType(CertType.mTLS);
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setCertPem("old-cert-pem");
        sampleCert.setKeyPem("old-key-pem");
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(VALID_RENEWED_CERT_PEM)
                .keyPem(VALID_RENEWED_KEY_PEM)
                .build();

        K8sCertVO result = k8sCertService.renewCert(command);

        assertThat(result.getCertPem()).isEqualTo(VALID_RENEWED_CERT_PEM.trim());
        assertThat(result.getKeyPem()).isEqualTo(VALID_RENEWED_KEY_PEM.trim());
        assertThat(result.getIssuer()).isEqualTo("O=RocketMQ Studio,CN=renewed.example.com");
        assertThat(result.getSan()).containsExactly("renewed.example.com", "broker.renewed.example.com");
        assertThat(result.getNotBefore()).isEqualTo(LocalDateTime.of(2026, 8, 28, 12, 31, 21));
        assertThat(result.getNotAfter()).isEqualTo(LocalDateTime.of(2028, 11, 5, 12, 31, 21));
        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getGmtModified()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void renewCertShouldPreserveCertificateChainAndUseLeafForMetadataAndKeyMatch() {
        sampleCert.setType(CertType.mTLS);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String certificateChain = VALID_RENEWED_CERT_PEM + "\n" + RSA_TRADITIONAL_CERT_PEM;

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(certificateChain)
                .keyPem(VALID_RENEWED_KEY_PEM)
                .build();

        K8sCertVO result = k8sCertService.renewCert(command);

        assertThat(result.getCertPem()).isEqualTo(certificateChain.trim());
        assertThat(result.getIssuer()).isEqualTo("O=RocketMQ Studio,CN=renewed.example.com");
        assertThat(result.getSan()).containsExactly("renewed.example.com", "broker.renewed.example.com");
    }

    @Test
    void renewCertShouldAcceptTraditionalRsaPrivateKeyPem() {
        sampleCert.setType(CertType.mTLS);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(RSA_TRADITIONAL_CERT_PEM)
                .keyPem(RSA_PKCS1_PRIVATE_KEY_PEM)
                .build();

        K8sCertVO result = k8sCertService.renewCert(command);

        assertThat(result.getKeyPem()).startsWith("-----BEGIN RSA PRIVATE KEY-----");
        assertThat(result.getIssuer()).isEqualTo("O=RocketMQ Studio,CN=rsa-traditional.example.com");
        assertThat(result.getSan()).containsExactly("rsa-traditional.example.com");
    }

    @Test
    void renewCertShouldRejectTraditionalRsaPrivateKeyWhenItDoesNotMatch() {
        sampleCert.setType(CertType.mTLS);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(RSA_TRADITIONAL_CERT_PEM)
                .keyPem(EC_SEC1_PRIVATE_KEY_PEM)
                .build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Private key does not match certificate public key")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verify(k8sCertRepository, never()).save(any(K8sCertVO.class));
    }

    @Test
    void renewCertShouldAcceptTraditionalEcPrivateKeyPem() {
        sampleCert.setType(CertType.mTLS);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(EC_TRADITIONAL_CERT_PEM)
                .keyPem(EC_SEC1_PRIVATE_KEY_PEM)
                .build();

        K8sCertVO result = k8sCertService.renewCert(command);

        assertThat(result.getKeyPem()).startsWith("-----BEGIN EC PRIVATE KEY-----");
        assertThat(result.getIssuer()).isEqualTo("O=RocketMQ Studio,CN=ec-traditional.example.com");
        assertThat(result.getSan()).containsExactly("ec-traditional.example.com");
    }

    @Test
    void renewCertShouldRejectTraditionalEcPrivateKeyWhenItDoesNotMatch() {
        sampleCert.setType(CertType.mTLS);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(EC_TRADITIONAL_CERT_PEM)
                .keyPem(RSA_PKCS1_PRIVATE_KEY_PEM)
                .build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Private key does not match certificate public key")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verify(k8sCertRepository, never()).save(any(K8sCertVO.class));
    }

    @Test
    void renewCertShouldRejectMtlsCertificateWhenPrivateKeyDoesNotMatch() {
        sampleCert.setType(CertType.mTLS);
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(VALID_RENEWED_CERT_PEM)
                .keyPem(MISMATCHED_KEY_PEM)
                .build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Private key does not match certificate public key")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verify(k8sCertRepository, never()).save(any(K8sCertVO.class));
    }

    @Test
    void renewCertShouldRejectExpiredReplacementCertificate() {
        k8sCertService = new K8sCertService(k8sCertRepository, operationAuditService,
                Clock.fixed(Instant.parse("2029-01-01T00:00:00Z"), ZoneOffset.UTC));
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(VALID_RENEWED_CERT_PEM)
                .build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Certificate is expired")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verify(k8sCertRepository, never()).save(any(K8sCertVO.class));
    }

    @Test
    void renewCertShouldNotMutateStoredCertWhenSaveFails() {
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setDaysRemaining(0);
        LocalDateTime originalNotBefore = sampleCert.getNotBefore();
        LocalDateTime originalNotAfter = sampleCert.getNotAfter();

        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenThrow(new IllegalStateException("save failed"));

        RenewCertDTO command = RenewCertDTO.builder()
                .id(1L)
                .certPem(VALID_RENEWED_CERT_PEM)
                .build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        assertThat(sampleCert.getStatus()).isEqualTo(CertStatus.expired);
        assertThat(sampleCert.getDaysRemaining()).isZero();
        assertThat(sampleCert.getNotBefore()).isEqualTo(originalNotBefore);
        assertThat(sampleCert.getNotAfter()).isEqualTo(originalNotAfter);
        assertThat(sampleCert.getGmtModified()).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    @Test
    void renewCertShouldThrowWhenNotFound() {
        when(k8sCertRepository.findById(999L)).thenReturn(Optional.empty());

        RenewCertDTO command = RenewCertDTO.builder().id(999L).build();

        assertThatThrownBy(() -> k8sCertService.renewCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Certificate not found: 999");
    }

    @Test
    void deleteCertShouldDeleteWhenFound() {
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.deleteById(1L)).thenReturn(true);

        DeleteCertDTO command = DeleteCertDTO.builder().id(1L).build();

        k8sCertService.deleteCert(command);

        verify(k8sCertRepository).deleteById(1L);
        verify(operationAuditService).record(eq("DELETE_K8S_CERTIFICATE"), eq("K8S_CERTIFICATE"),
                eq("1"), eq(null), eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteCertShouldRejectConcurrentRemoval() {
        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.deleteById(1L)).thenReturn(false);

        DeleteCertDTO command = DeleteCertDTO.builder().id(1L).build();

        assertThatThrownBy(() -> k8sCertService.deleteCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Certificate not found: 1")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));
        verifyNoInteractions(operationAuditService);
    }

    @Test
    void deleteCertShouldThrowWhenNotFound() {
        when(k8sCertRepository.findById(999L)).thenReturn(Optional.empty());

        DeleteCertDTO command = DeleteCertDTO.builder().id(999L).build();

        assertThatThrownBy(() -> k8sCertService.deleteCert(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Certificate not found: 999");
    }

    private K8sCertVO copyWithExpiry(Long id, LocalDateTime notAfter, CertStatus status,
                                     int daysRemaining) {
        K8sCertVO cert = K8sCertVO.builder()
                .k8sId(sampleCert.getK8sId())
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
        cert.setGmtCreate(sampleCert.getGmtCreate());
        cert.setGmtModified(sampleCert.getGmtModified());
        return cert;
    }

    private static final String VALID_RENEWED_CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDjzCCAnegAwIBAgIUVO46kDCJ4FzwSPrFNV+qJNHVSFkwDQYJKoZIhvcNAQEL
            BQAwODEcMBoGA1UEAwwTcmVuZXdlZC5leGFtcGxlLmNvbTEYMBYGA1UECgwPUm9j
            a2V0TVEgU3R1ZGlvMB4XDTI2MDgyODEyMzEyMVoXDTI4MTEwNTEyMzEyMVowODEc
            MBoGA1UEAwwTcmVuZXdlZC5leGFtcGxlLmNvbTEYMBYGA1UECgwPUm9ja2V0TVEg
            U3R1ZGlvMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApyCGzZweLcqx
            jIZ0YEgGqJj9l/UyU9sTc84aLiZKN1nklQc00DW8J3wYafowIMwPnGsh8YJDDE4f
            BytQDcUN/e+WHmZ+7M/Qp3ZK8+nO7X/WIPQBi5DrWAdtfrLMEuqqijZE90SH4/2Y
            PmRO+F4g3fdmYM+myXPkYYpzGtuiPK2nwHBk+TnAj5QvVG1NSYmDahChhSdn+Nna
            JuDpe4XbNGclFeIuBnU9iH9hcMHg3gVjoIId1iXxPxQatw2YXJzEEERW9Zde5OmQ
            8hR+Iww/e7mWho7RNO5aYjlOJo2Lc4T0AhgcpCceoE0OMehcZIDkj4TcC1Ed0dnn
            Sp8AhVPQowIDAQABo4GQMIGNMB0GA1UdDgQWBBQ84QhlHL9kov7YFxoy+9Jj4+vC
            mDAfBgNVHSMEGDAWgBQ84QhlHL9kov7YFxoy+9Jj4+vCmDAPBgNVHRMBAf8EBTAD
            AQH/MDoGA1UdEQQzMDGCE3JlbmV3ZWQuZXhhbXBsZS5jb22CGmJyb2tlci5yZW5l
            d2VkLmV4YW1wbGUuY29tMA0GCSqGSIb3DQEBCwUAA4IBAQBYfVHNOLBkYo/755un
            cLdwpbyiteMWo9AE5eqAgN1kx1jysPMkLwbP1jD/T3upUqkdYqxldxC/9SXHtgar
            zb8rRjSG61KzkDv2eKVR9xVwfBJEj7R79d3Yu72VecQyWHLIx55boXC/yenMYCxK
            wrYogCjLdmXuMp1L8gnuyxPp5rWAw+62xZZi7x4jrEzIYd74o0vMnRXc/ZHnnVfM
            E2RfI3coIz+nWQEGLKcR5PV9dVfLWMogb9iHIDI51YI74k3F8PL1QSohsfL4CkxO
            chctzYBkC9utC5R0bCp2yxlInf7geD3YdiO/nCOPpAXjZVu04tWUF6Jz09htc9AL
            goXf
            -----END CERTIFICATE-----
            """;

    private static final String VALID_RENEWED_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCnIIbNnB4tyrGM
            hnRgSAaomP2X9TJT2xNzzhouJko3WeSVBzTQNbwnfBhp+jAgzA+cayHxgkMMTh8H
            K1ANxQ3975YeZn7sz9Cndkrz6c7tf9Yg9AGLkOtYB21+sswS6qqKNkT3RIfj/Zg+
            ZE74XiDd92Zgz6bJc+RhinMa26I8rafAcGT5OcCPlC9UbU1JiYNqEKGFJ2f42dom
            4Ol7hds0ZyUV4i4GdT2If2FwweDeBWOggh3WJfE/FBq3DZhcnMQQRFb1l17k6ZDy
            FH4jDD97uZaGjtE07lpiOU4mjYtzhPQCGBykJx6gTQ4x6FxkgOSPhNwLUR3R2edK
            nwCFU9CjAgMBAAECggEAHsLDXQvLmDUTCeDjgacwJo8GOhZk3X9YrLt2ISFmDpsK
            kg2CAIKrb38NRVBJ1HeKtgkX9co6igTE/D5SHT60TRVwhYbq/K5hYrlIoW1a62bY
            pDcVI7mYP5saYbQKEL9FhWvotLRV6LZP88flqxw0I3a6Tr5ZngGpOvTPK9XaHh2y
            Ikz8FgiidKSVj2hotphcnFwGcBM6S7Dkvfrr6cnjnRF0a0OePoYhrWLTwTg0Rq77
            KJd+w+9Os0LuZ+fwGuzsDAPy2hF5ciYRO4NEjQ2DaT8oj/I+u3LByeHTM1J9DcaO
            6ZtMNbmDfhRhKUZATPUmPsY3Za9Bd/F1XWpJMrQxXQKBgQDZZ/aeUqJN8u9LK7M6
            ZNsIzaX2nQp/V6gVAwDBPPW37+BotTEc3kIbKXT+TRhoZLC3udlIMZFjzkuhAcw6
            OwXf5ZCPD/5mCqH5u4VOUxrn85CmBl/MCH8TcmG/XMiStxOYQ1uz/5vmipyFZXPL
            RX0KuZwwV003nhy4xEMKdsx2BwKBgQDEy6DJL70+6EKVO07dQKjZvMy+EZmYa3m6
            NoxXC/uR1CkneVHnYH7wFV2T4GBAhb2ZsLfwBjmOkFwebqaBNGyQIDfijWY3q2Xx
            Gb/Ws5ksLcVCkSp+95Rb4X41QbS/2nh+8Ifi3/6n59nfC3pxqh71b/y4/aJFZ0JG
            I0xx5FnJhQKBgHssvvqGoPR+/nrdgIdGGx9KvIwT/52UgWOeNvBE5IbZPpC0j+Xm
            Oxf+jg2CiqCi48jEYEnZ46Djgc/wH9CiHjrzasrTafRQc+L1DpsI1Ma0JbEbDW2h
            JrZS6PSt0enmFhD/oNZDrQWaZQHjMA7sCONptAdjfxlS2L0KXV1xX3hDAoGAVJMC
            fZPzq8ZbXxEG+pUgO7sk7oZX0SZXQQzSUVKIAgsAyMMdzOcuhnVYKwYht3kCm7tT
            wWabc8ZcoIODMUHbajE+czG7fS8+91fOlzHGITNmdA45CinSa45EFBUx3cXBRSSP
            8ZO8OGKuwmmHbLPk7Cv9m279PwB6ffQLlWLCp5UCgYAr+qcYU/PoO0mjoTRHDmQt
            8fnFQ4wGHR1JaM7McaGb1m0YDzWhgZqBVp5/0Am/ihKbQXi4Q3iSQrStuYyn8Lyz
            1tl2USkDMiI1auXq0FVXwKIQpCS1rs8nv2ynJuQt4qdSm06ecT4NFHO2Q5Tyb/Os
            hotbBYMZlc54G0w1LZidXA==
            -----END PRIVATE KEY-----
            """;

    private static final String MISMATCHED_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDFva1HBzjxHSOo
            6YjAREaqKUeQcYCAhkl4zaUg6ZB+z/Sn4dcNB0NGaW4lJU2yvZLalM2h+Ou+qTTq
            Rw7TbychjmrB1ecEB8tFNJz/PTWSjSgXKpYqxRqKK530U1lqIMY84TnjhZAh5qIq
            xkTYH/lJKDwVhIUI/qz2tLjRDLJ1LxjZ3p0TM++xUMSUIOPhZvXQo7Gt4qK3oMgo
            mZQDpGboplFf5LsWoUXwe1eGklsB/8kdK2OpqMnwzagggvj+ZeH/kEjRnibtnfq4
            rT8Nv9rdxoYYX42kRM0P/FBSTQzR9VrieQpHw+e2a5ngVFmYGLhnhXk6FN1H6uRA
            ZicQhqtLAgMBAAECggEAIbBinKbM2zthL252N3eKaIQ25pOH1p3KV7QFjZltXkWU
            w6N07YnOuujMdLSpy6mDowzWCeHsXCPc2Ys4qeoWix+F7bdBMA0Z4xUHKG8nuOum
            qGe/hNLm5iJvO0iWA8BIteeTYsGHIFd4SnxUD1RHNuENd4cH2VP2aOO6VxdoMhGC
            xS+xYSQTuYavvbaFAjBjwFqmcPrlZx1B1Nm9ykzlUlrZm7L3bqcjg/lksXmrbTi3
            e/UWzubCUxuagy91pRBJxOUBvouhIGej0Aa7uNAleIlfQkTJVHv2tT/9Jk8UrvmM
            2d+wkzEUFK3ur9FhUb2IKeq354Z6Nmmy5bqvEy0GuQKBgQDvVTxKjo3mlp8L8FgE
            vCMl5yhyG0uy8XTO/ryqUyGahYEiaWVVlsJ5wYTIsayyf9H9P9hb8F4NrZYUfx8Q
            QUvWvO5XFhhGTAyOE2Z3z5Bziq9MriMtxtgqOkCcqbkY9bopXfvaz5LJxmcOZEWu
            GfUshhpJWQRH6VMcLVCK/PaukwKBgQDTgvMhNz7UTJwpu7oLNTyFwg80tpzjmWbR
            k4Yd1natoqmmRDK5F2X24kKmv5Dik1k3gaVegXA3lSawCAP+pUgEm2cQBNZfd31P
            z2TL6FurrfUCX6dUizuob5LzMix08T05/bf5MNNe2gSsjVrSQsVTsnz+mg9ooOP8
            gCNzeXxLaQKBgQDrMgQx8K2acWKTRPn6jTitQuEIYbKeg5Ka6NNXPqLDS3d/7btb
            xPAQ3xAyegiQ0fP2wAtLLof/QRs/wT0xqDlzKe+/PUNVsd6UsJP+Ich/A0cKQAbq
            MYK03NIqItB3quPrSyT5/wrtp0AXcIrZcUDzJEYo1oXSdYTrJ80DCV0SaQKBgB7t
            g/20ZVSHy0Hy+FZRN4Nbh/uuRCynrrgweSj9xibHpUTxrfUQrdE27oYRdu8amq4a
            IAM8rBsEjT6qPWNL6cb6rkxSWMJm54T3D4cdd+IXsr7hG8eqAFQ11GgJSyTibZCA
            QBmJAS9ac9qDZOdf6hi9/bcA8gXbmNrAJe7psboZAoGAdof3MLPIMpNdYklL6UOM
            Qs7MrN4EXKcpkGaEkPfJo+QX7s/gZiIakWCx/RrLAH++C7ZdGMhwf+h1YdTSmB/B
            F+/MG/J91ui7TVL2o9s4WJQRtbL8JJ3dk+VQttaimbTrwh9JExby7wnkpsWEudHD
            BBRh5yR/XVvk+eAJKLcslzo=
            -----END PRIVATE KEY-----
            """;

    private static final String RSA_TRADITIONAL_CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDiTCCAnGgAwIBAgIUEfeFLZXcRpgV9NI41trrXMxBIkIwDQYJKoZIhvcNAQEL
            BQAwQDEkMCIGA1UEAwwbcnNhLXRyYWRpdGlvbmFsLmV4YW1wbGUuY29tMRgwFgYD
            VQQKDA9Sb2NrZXRNUSBTdHVkaW8wHhcNMjYwODI5MDgxODUzWhcNMzYwODI2MDgx
            ODUzWjBAMSQwIgYDVQQDDBtyc2EtdHJhZGl0aW9uYWwuZXhhbXBsZS5jb20xGDAW
            BgNVBAoMD1JvY2tldE1RIFN0dWRpbzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
            AQoCggEBANJnixy4w6vpLp2GGCAe1do01pTNnToeRS1bG7XnoAIQ/blVnB0Zdukb
            ++VSXbmMFRTNLVuHbOmYLhgwRoyWY4G7mirVCKTFbe2IsNPaDA6ujRanhfTCBnjg
            ctcxHZvgR/tYwBwYqCxTpADzsW5K/1Ywo3gHJu2PFg6UKJAX/yIwDtCt3aoWhVgM
            Z13e/v3ZElngwkMa3QKWezchfCSeGZ5xrP+ykUApJiNE7SqFZemmf4GcB9jcEjqR
            Wx6zpwt36L8XANV9acdt8h3fa3DuDUuFYNb06jxejU3z4QOH18/lJNLRi8y2oeMu
            b1myY3FT8Ceqpi0tboPd7M4eKlxJit0CAwEAAaN7MHkwHQYDVR0OBBYEFIC96/U7
            BPvJRpo3azpHORS2T8H9MB8GA1UdIwQYMBaAFIC96/U7BPvJRpo3azpHORS2T8H9
            MA8GA1UdEwEB/wQFMAMBAf8wJgYDVR0RBB8wHYIbcnNhLXRyYWRpdGlvbmFsLmV4
            YW1wbGUuY29tMA0GCSqGSIb3DQEBCwUAA4IBAQAN1lELl6d39zn6e71w5Xm7NGq3
            aZkwGrYNxJedDoU0NOfgdCTA1niP/Dqj3WL1if6t3eIlmqjeoMDfcZ8gIKoj3a86
            cnw7JJ2WrBpMzl4bKTA/+JQLpOJDa8+pqiZmmwfjUPdkesSBKSAx5A5xjfMBt8OW
            E1mkvsCb+w4kV7OyhUQx/WY1Fwn+bgGSK5H1l4LnlCh/tTussooOIeRZdEAk7Qvm
            LXxxqopAdXkpx15VDXv9j/MW3kleBNIemvTiWtWcai/zpLSNtnOrFyADD9T0CZ7g
            /L3VBPmF2wUE7mpqI/6X6KHAUiHANVSi5mm+goggVdfhVuRg25knQu+pVBGR
            -----END CERTIFICATE-----
            """;

    private static final String RSA_PKCS1_PRIVATE_KEY_PEM = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA0meLHLjDq+kunYYYIB7V2jTWlM2dOh5FLVsbteegAhD9uVWc
            HRl26Rv75VJduYwVFM0tW4ds6ZguGDBGjJZjgbuaKtUIpMVt7Yiw09oMDq6NFqeF
            9MIGeOBy1zEdm+BH+1jAHBioLFOkAPOxbkr/VjCjeAcm7Y8WDpQokBf/IjAO0K3d
            qhaFWAxnXd7+/dkSWeDCQxrdApZ7NyF8JJ4ZnnGs/7KRQCkmI0TtKoVl6aZ/gZwH
            2NwSOpFbHrOnC3fovxcA1X1px23yHd9rcO4NS4Vg1vTqPF6NTfPhA4fXz+Uk0tGL
            zLah4y5vWbJjcVPwJ6qmLS1ug93szh4qXEmK3QIDAQABAoIBAEx8d4WpZPhJfDin
            Vn3Wt8brDlZpqI5IEr26MQifevtFUfbduDKb3y4+jgN/PbMFyWQWcjajtGP2fkss
            wXi58tJmcFTBvLKpUpzW4/EfguKTcZaar4eaQOAQN68im7Deh0xHpw6PqBL1FNmD
            vSxq9wdOBx7K+svBCDOkiFpZXtX+GaPAlRCNwsnLaS+Q5d+w3wW7A25viiKWNoES
            Y1UV17zc8eiUuQY5Wl60Nl8WfbMX+vJFt2thB1cZI2aJlJdsC38LAdHTq64WwPmX
            FTvA7q/8tDc7Rv8/ChfQlc8z8DDlFduZQVvcfBE9L2Dybk0eKhEF0abq819zONew
            OFVPh8ECgYEA60ZKupNtDdGJ7VlfGP23HVEAEV5nMGlQS+ng/vFCvqnr4joC2PLr
            32Oj3Uy8jkNG7MhBF4vcGP6tRtftKp99lXKdK+sFtru6uHthYyWIUOmlrWaBtXs7
            9CxLFknEnTBjDeh9F/eTJOQsTIg6mdnNABFxojz9D3bn9PGmjfGFuqMCgYEA5PBm
            /yS6y7BOscarVm9MF91xswbiLKlufXNTAok41P6dtAS2aGjk5jXuM++G21RNirSI
            vTn/QbM0FafGXc7UTE1wBcb91GF3O4Wxn49qGAKA7HoVWksgx05q7Gz6bJofKJ3U
            6jxReskYet17v/gEMh2d6HcEP03g52ktrpT5fH8CgYAyRg7p117SORgz84jymiRq
            y0gsbfO20Ior7on5cCxG+aBB8wtwuFfWoD//pcoUzCN3rULbeTNK1ADKxpETLolz
            Sc5z+AB8j5jSmuqwePCr+YFBkEnfMboZ7u0Mki7FN/Wynx8749c5ZthgciuzfGrl
            vNR/SnD4wPvHx2tDoXxl6QKBgHJM1B9uZxRq4d9AISr2RjdkB/Ap76H8tX1MppUN
            jaJJvNKzx545QI7vPg4P+HRoko49tEdFPXu/zLFDInaTXMr7noJD51axkqXVCelv
            4Lg8B8II8cAy4hqfvCJuBllSWVwd8L9BfiyfWel9ytr9KJsczknRof05FKB0kqon
            FqhhAoGBALH4wBw00eQu3eYEnqjG4cI4YWiO62LIxCDoCKMLJLck9vpY/N91/bjb
            K6urba62nMqoecyGXX9YWE45SBJgiOcPUMXVDvzBc0GJBqp3l35FdhtItqif2lYT
            TgFvMFEW74675NsSzF8QnkfjTVgRPDrMCfsd0DwaOPSr5E3CIXXd
            -----END RSA PRIVATE KEY-----
            """;

    private static final String EC_TRADITIONAL_CERT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIB+jCCAaCgAwIBAgIUGwZU8YZ6oYdA9lBoAQlTKwXVpaEwCgYIKoZIzj0EAwIw
            PzEjMCEGA1UEAwwaZWMtdHJhZGl0aW9uYWwuZXhhbXBsZS5jb20xGDAWBgNVBAoM
            D1JvY2tldE1RIFN0dWRpbzAeFw0yNjA4MjkwODE4NTNaFw0zNjA4MjYwODE4NTNa
            MD8xIzAhBgNVBAMMGmVjLXRyYWRpdGlvbmFsLmV4YW1wbGUuY29tMRgwFgYDVQQK
            DA9Sb2NrZXRNUSBTdHVkaW8wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ0RhZR
            QBjblcTP6eEFpY8QHKU/FbHM8p9qpBzc+ULPtZhHZLU4vsTYrSTl+wn+fABpyl8g
            Yf5nXzDlvYBVXZ4fo3oweDAdBgNVHQ4EFgQUrUqh0sGWajbgBKnKsKl+8M0yn7cw
            HwYDVR0jBBgwFoAUrUqh0sGWajbgBKnKsKl+8M0yn7cwDwYDVR0TAQH/BAUwAwEB
            /zAlBgNVHREEHjAcghplYy10cmFkaXRpb25hbC5leGFtcGxlLmNvbTAKBggqhkjO
            PQQDAgNIADBFAiA9avA1agW/RaJxNvEAmsaKCncSML3I4fRsH3wUSxs5AQIhAOJ9
            GMvIaq7Atb8kXFG8R3+IHIhC5zpRXgeT7jjPK0GY
            -----END CERTIFICATE-----
            """;

    private static final String EC_SEC1_PRIVATE_KEY_PEM = """
            -----BEGIN EC PRIVATE KEY-----
            MHcCAQEEIPway8YYgYUZsKG4zIzevx72qP6FKj2xEnKY3OiBPrXooAoGCCqGSM49
            AwEHoUQDQgAENEYWUUAY25XEz+nhBaWPEBylPxWxzPKfaqQc3PlCz7WYR2S1OL7E
            2K0k5fsJ/nwAacpfIGH+Z18w5b2AVV2eHw==
            -----END EC PRIVATE KEY-----
            """;
}
