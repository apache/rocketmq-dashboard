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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class K8sCertServiceTest {

    @Mock
    private K8sCertRepository k8sCertRepository;

    @InjectMocks
    private K8sCertService k8sCertService;

    private K8sCertVO sampleCert;

    @BeforeEach
    void setUp() {
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

        assertThat(result.getName()).isEqualTo("new-tls-cert");
        assertThat(result.getNamespace()).isEqualTo("default");
        assertThat(result.getCluster()).isEqualTo("test-cluster");
        assertThat(result.getType()).isEqualTo(CertType.TLS);
        assertThat(result.getIssuer()).isEqualTo("vault");
        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getSan()).containsExactly("svc.example.com");
        assertThat(result.getNotBefore()).isNotNull();
        assertThat(result.getNotAfter()).isAfter(result.getNotBefore());
        assertThat(result.getDaysRemaining()).isGreaterThan(0);
        verify(k8sCertRepository).save(any(K8sCertVO.class));
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
        assertThat(saved.getNotAfter()).isAfter(saved.getNotBefore());
        long expectedDays = java.time.temporal.ChronoUnit.DAYS.between(saved.getNotBefore(), saved.getNotAfter());
        assertThat(saved.getDaysRemaining()).isEqualTo((int) expectedDays);
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
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(k8sCertRepository).save(any(K8sCertVO.class));
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
    void renewCertShouldRenewCertValidity() {
        sampleCert.setStatus(CertStatus.expired);
        sampleCert.setDaysRemaining(0);

        when(k8sCertRepository.findById("cert-1")).thenReturn(Optional.of(sampleCert));
        when(k8sCertRepository.save(any(K8sCertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RenewCertDTO command = RenewCertDTO.builder().id("cert-1").build();

        K8sCertVO result = k8sCertService.renewCert(command);

        assertThat(result.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(result.getDaysRemaining()).isGreaterThan(0);
        assertThat(result.getNotBefore()).isNotNull();
        assertThat(result.getNotAfter()).isAfter(result.getNotBefore());
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(k8sCertRepository).save(any(K8sCertVO.class));
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
}
