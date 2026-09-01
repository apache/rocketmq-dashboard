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
import java.time.ZoneId;
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
    void renewCertShouldRenewCertValidity() {
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setDaysRemaining(0);
        LocalDateTime originalNotBefore = sampleCert.getNotBefore();
        LocalDateTime originalNotAfter = sampleCert.getNotAfter();

        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RenewCertDTO command = RenewCertDTO.builder().id(1L).build();

        K8sCertVO result = k8sCertService.renewCert(command);
        LocalDateTime now = LocalDateTime.now(CLOCK);

        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getDaysRemaining()).isEqualTo(365);
        assertThat(result.getNotBefore()).isEqualTo(now);
        assertThat(result.getNotAfter()).isEqualTo(now.plusYears(1));
        assertThat(result.getGmtModified()).isEqualTo(now);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getGmtCreate()).isEqualTo(sampleCert.getGmtCreate());
        assertThat(result).isNotSameAs(sampleCert);
        assertThat(sampleCert.getStatus()).isEqualTo(CertStatus.expired);
        assertThat(sampleCert.getDaysRemaining()).isZero();
        assertThat(sampleCert.getNotBefore()).isEqualTo(originalNotBefore);
        assertThat(sampleCert.getNotAfter()).isEqualTo(originalNotAfter);
        verify(k8sCertRepository).save(any(K8sCertVO.class));
        verify(operationAuditService).record(eq("RENEW_K8S_CERTIFICATE"), eq("K8S_CERTIFICATE"),
                eq("1"), eq(null), eq("k8sId=rocketmq-tls, cluster=prod-cluster"),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void renewCertShouldNotMutateStoredCertWhenSaveFails() {
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setDaysRemaining(0);
        LocalDateTime originalNotBefore = sampleCert.getNotBefore();
        LocalDateTime originalNotAfter = sampleCert.getNotAfter();

        when(k8sCertRepository.findById(1L)).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenThrow(new IllegalStateException("save failed"));

        RenewCertDTO command = RenewCertDTO.builder().id(1L).build();

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
    private static final String FIXED_PEM =
                "-----BEGIN CERTIFICATE-----\n" +
                "MIIC6jCCAdKgAwIBAgICEAAwDQYJKoZIhvcNAQELBQAwDTELMAkGA1UEAwwCY2Ew\n" +
                "HhcNMjUwMTAxMDAwMDAwWhcNMjYwMTAxMDAwMDAwWjAUMRIwEAYDVQQDDAl6b25l\n" +
                "LXRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDefm2k8W00HEFq\n" +
                "tSjmSMD+bZ4bz8acwnKPYsNdUgPp/Nl7SlBmtV1ztUtqHtKYGvjT/pzgjXE2iU8X\n" +
                "yzgZ9aIvUIkxdcoHoK2rDKX8FySMAWtTjLS9FEst+O8+rBvDkPU0tD8RP8Yr8R8d\n" +
                "sC8L8bMOnLSRQE45wvWmy72QE/HZe4SqNvHMu7ub6CUTDVArHlT+ioLIGlGJWU3U\n" +
                "/hZUe5f5FzBvoVnLD6qSkzn1X+6MIH6g5V6P8pdllhydSnhjY+0eA2RR0MOGb+g2\n" +
                "VclMTKR07RgwQ/okIJFLDbrOwb5unduDKPNjNKF9ZvyV5lcTTJUcd+n9U/v3+500\n" +
                "gCHRuGEzAgMBAAGjTTBLMAkGA1UdEwQCMAAwHQYDVR0OBBYEFB+S1UkHBWBGhEa3\n" +
                "m4+TrH5IVdcrMB8GA1UdIwQYMBaAFAHvq5UN1IDLM7OMWn929EWM21FKMA0GCSqG\n" +
                "SIb3DQEBCwUAA4IBAQB1Nf2+zs7DDHMYXxdd8fjRTK9BVjYeDlxt0Ig14OZSTdSp\n" +
                "hVdsC6jeTGgJbSAdM9Prc9TNvgzLARJ+I2Uxsf/2bKk7xdQO4utQrK0qIMqZQz6d\n" +
                "t7cVJrca30ZmeaLR18KrwjxLCCOqqxzBh9AGP6yyTZYaAcnSzjcgnzEQPj0VD8pl\n" +
                "mmIeKDUqx68OBolQS/ls8oUF6LfUbqtb8r64DV9WnsWE+by6P/eEwB7soA/pjUk8\n" +
                "iXVijklQHUfVAYvNje75NyCR76+LPgWWjfq0uBGtr2lFYB0N2ao5Q+7WQvz/H4o4\n" +
                "qayioHxN97q6MOUJiqH6tbubc2/NEIUu2mhvvD5D\n" +
                "-----END CERTIFICATE-----\n" ;
    @Test
    void createCertShouldDerivePemValidityInTheClockZone() {
        // A PEM's validity window is an absolute instant. Converting it in the JVM default
        // zone while expiry checks use LocalDateTime.now(clock) drifts when the zones differ.
        ZoneId clockZone = ZoneId.systemDefault().equals(ZoneOffset.UTC)
                ? ZoneId.of("Pacific/Kiritimati")
                : ZoneOffset.UTC;
        Clock zoneClock = Clock.fixed(Instant.parse("2025-07-01T12:00:00Z"), clockZone);
        K8sCertService zoneService = new K8sCertService(k8sCertRepository, operationAuditService, zoneClock);

        CreateCertDTO command = CreateCertDTO.builder()
                .k8sId("zone-test")
                .cluster("test-cluster")
                .type("TLS")
                .certPem(FIXED_PEM)
                .build();

        ArgumentCaptor<K8sCertVO> captor = ArgumentCaptor.forClass(K8sCertVO.class);
        when(k8sCertRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        zoneService.createCert(command);

        K8sCertVO saved = captor.getValue();
        // The embedded certificate is valid 2025-01-01T00:00:00Z .. 2026-01-01T00:00:00Z.
        assertThat(saved.getNotBefore())
                .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2025-01-01T00:00:00Z"), clockZone));
        assertThat(saved.getNotAfter())
                .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), clockZone));
        // Proves the conversion used the clock zone, not the JVM default zone.
        assertThat(saved.getNotAfter())
                .isNotEqualTo(LocalDateTime.ofInstant(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault()));
    }

}