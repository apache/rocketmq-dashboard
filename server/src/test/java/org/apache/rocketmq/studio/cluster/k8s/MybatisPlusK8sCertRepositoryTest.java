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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MybatisPlusK8sCertRepositoryTest {

    @Test
    void findByIdNormalizesPersistedCertificateEnums() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setCertType(" mtls ");
        entity.setStatus(" EXPIRING ");
        when(mapper.selectById(1L)).thenReturn(entity);

        assertThat(repository(mapper).findById(1L)).get()
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
        when(mapper.selectById(1L)).thenReturn(certificate());
        when(mapper.updateById(any(RmqK8sCertificate.class))).thenReturn(0);
        K8sCertVO cert = K8sCertVO.builder().name("broker").build();
        cert.setId(1L);

        assertThatThrownBy(() -> repository(mapper).save(cert))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Certificate update was not applied: 1")
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) error).getCode()).isEqualTo(409));
    }

    @Test
    void deleteByIdShouldReportWhetherARowWasRemoved() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        when(mapper.deleteById(1L)).thenReturn(1);
        when(mapper.deleteById(2L)).thenReturn(0);

        MybatisPlusK8sCertRepository repository = repository(mapper);

        assertThat(repository.deleteById(1L)).isTrue();
        assertThat(repository.deleteById(2L)).isFalse();
    }

    @Test
    void findByIdSurfacesInvalidPersistedCertificateType() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setCertType("UNKNOWN_TYPE");
        when(mapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("certificate type");
    }

    @Test
    void findByIdSurfacesMalformedPersistedSanJson() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setSan("not-json");
        when(mapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SAN JSON");
    }

    @Test
    void findByIdSurfacesNullPersistedSanJson() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setSan("null");
        when(mapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SAN JSON");
    }

    @Test
    void findByIdSurfacesInvalidPersistedCertificateStatus() {
        RmqK8sCertificateMapper mapper = mock(RmqK8sCertificateMapper.class);
        RmqK8sCertificate entity = certificate();
        entity.setStatus("unknown");
        when(mapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> repository(mapper).findById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("certificate status");
    }

    private MybatisPlusK8sCertRepository repository(RmqK8sCertificateMapper mapper) {
        return new MybatisPlusK8sCertRepository(mapper, new ObjectMapper());
    }

    private RmqK8sCertificate certificate() {
        RmqK8sCertificate entity = new RmqK8sCertificate();
        entity.setId(1L);
        entity.setCertType("TLS");
        entity.setStatus("valid");
        entity.setSan("[\"broker.example\"]");
        return entity;
    }
}
