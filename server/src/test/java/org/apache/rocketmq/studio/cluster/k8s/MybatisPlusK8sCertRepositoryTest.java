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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqK8sCertificate;
import org.apache.rocketmq.studio.persistence.mapper.RmqK8sCertificateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlusK8sCertRepositoryTest {

    @Test
    void findByIdNormalizesPersistedCertificateEnums() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setCertType(" mtls ");
        entity.setStatus(" EXPIRING ");
        when(mapper.selectById("cert-1")).thenReturn(entity);

        assertThat(repository(mapper).findById("cert-1")).get()
                .satisfies(cert -> {
                    assertThat(cert.getType()).isEqualTo(
                            org.apache.rocketmq.studio.common.domain.enums.CertType.mTLS);
                    assertThat(cert.getStatus()).isEqualTo(
                            org.apache.rocketmq.studio.common.domain.enums.CertStatus.expiring);
                });
    }

    @Test
    void saveShouldReportALostConcurrentUpdate() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        when(mapper.selectById("cert-1")).thenReturn(certificate());
        when(mapper.updateById(any(RmqK8sCertificate.class))).thenReturn(0);
        K8sCertVO cert = K8sCertVO.builder().name("broker").build();
        cert.setId("cert-1");

        assertThatThrownBy(() -> repository(mapper).save(cert))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Certificate update was not applied: cert-1")
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getCode()).isEqualTo(409));
    }

    @Test
    void deleteByIdShouldReportWhetherARowWasRemoved() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        when(mapper.deleteById("deleted")).thenReturn(1);
        when(mapper.deleteById("missing")).thenReturn(0);

        MybatisPlusK8sCertRepository repository = repository(mapper);

        assertThat(repository.deleteById("deleted")).isTrue();
        assertThat(repository.deleteById("missing")).isFalse();
    }

    @Test
    void findByIdSurfacesInvalidPersistedCertificateType() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setCertType("UNKNOWN_TYPE");
        when(mapper.selectById("cert-1")).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById("cert-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("certificate type");
    }

    @Test
    void findByIdSurfacesMalformedPersistedSanJson() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setSan("not-json");
        when(mapper.selectById("cert-1")).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById("cert-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SAN JSON");
    }

    @Test
    void findByIdSurfacesNullPersistedSanJson() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setSan("null");
        when(mapper.selectById("cert-1")).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById("cert-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SAN JSON");
    }

    @Test
    void findByIdNormalizesLegacySanValues() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setSan("[\" broker.example \",null,\"\",\"broker.example\",\"api.example\"]");
        when(mapper.selectById("cert-1")).thenReturn(entity);

        assertThat(repository(mapper).findById("cert-1")).get()
                .extracting(K8sCertVO::getSan)
                .isEqualTo(List.of("broker.example", "api.example"));
    }

    @Test
    void saveNormalizesSanValuesBeforePersistence() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        K8sCertVO cert = K8sCertVO.builder()
                .name("broker")
                .san(Arrays.asList(" broker.example ", null, "", "broker.example", "api.example"))
                .build();

        repository(mapper).save(cert);

        ArgumentCaptor<RmqK8sCertificate> entity = ArgumentCaptor.forClass(RmqK8sCertificate.class);
        verify(mapper).insert(entity.capture());
        assertThat(entity.getValue().getSan()).isEqualTo("[\"broker.example\",\"api.example\"]");
    }

    @Test
    void findByIdSurfacesInvalidPersistedCertificateStatus() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setStatus("unknown");
        when(mapper.selectById("cert-1")).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById("cert-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("certificate status");
    }

    private MybatisPlusK8sCertRepository repository(RmqK8sCertificateMapper mapper) {
        return new MybatisPlusK8sCertRepository(mapper, new ObjectMapper());
    }

    private RmqK8sCertificate certificate() {
        RmqK8sCertificate entity = new RmqK8sCertificate();
        entity.setId("cert-1");
        entity.setCertType("TLS");
        entity.setStatus("valid");
        entity.setSan("[\"broker.example\"]");
        return entity;
    }
}
