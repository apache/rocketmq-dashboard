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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.common.domain.enums.CertStatus;
import com.rocketmq.studio.common.domain.enums.CertType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(K8sCertController.class)
@AutoConfigureMockMvc(addFilters = false)
class K8sCertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private K8sCertService k8sCertService;

    @Test
    void listCertsShouldReturnAllCerts() throws Exception {
        K8sCertVO cert1 = buildCert("cert-1", "rocketmq-tls", CertType.TLS, CertStatus.valid);
        K8sCertVO cert2 = buildCert("cert-2", "broker-mtls", CertType.mTLS, CertStatus.expiring);
        when(k8sCertService.listCerts()).thenReturn(Arrays.asList(cert1, cert2));

        mockMvc.perform(get("/api/k8s-certs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("cert-1"))
                .andExpect(jsonPath("$.data[0].name").value("rocketmq-tls"))
                .andExpect(jsonPath("$.data[0].type").value("TLS"))
                .andExpect(jsonPath("$.data[0].status").value("valid"))
                .andExpect(jsonPath("$.data[1].id").value("cert-2"))
                .andExpect(jsonPath("$.data[1].name").value("broker-mtls"));
    }

    @Test
    void listCertsShouldReturnEmptyArrayWhenNoCerts() throws Exception {
        when(k8sCertService.listCerts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/k8s-certs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void createCertShouldReturnCreatedCert() throws Exception {
        K8sCertVO createdCert = buildCert("cert-new", "new-cert", CertType.TLS, CertStatus.valid);
        createdCert.setNamespace("default");
        createdCert.setCluster("test-cluster");
        createdCert.setIssuer("vault");
        createdCert.setSan(List.of("svc.example.com"));
        when(k8sCertService.createCert(any(CreateCertDTO.class))).thenReturn(createdCert);

        CreateCertDTO command = CreateCertDTO.builder()
                .name("new-cert")
                .namespace("default")
                .cluster("test-cluster")
                .type("TLS")
                .issuer("vault")
                .san(List.of("svc.example.com"))
                .build();

        mockMvc.perform(post("/api/k8s-certs/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("cert-new"))
                .andExpect(jsonPath("$.data.name").value("new-cert"))
                .andExpect(jsonPath("$.data.namespace").value("default"))
                .andExpect(jsonPath("$.data.cluster").value("test-cluster"))
                .andExpect(jsonPath("$.data.type").value("TLS"))
                .andExpect(jsonPath("$.data.status").value("valid"))
                .andExpect(jsonPath("$.data.san[0]").value("svc.example.com"));
    }

    @Test
    void createCertShouldAcceptMinimalCommand() throws Exception {
        K8sCertVO createdCert = buildCert("cert-min", "minimal-cert", CertType.TLS, CertStatus.valid);
        when(k8sCertService.createCert(any(CreateCertDTO.class))).thenReturn(createdCert);

        String json = """
                {
                    "name": "minimal-cert",
                    "namespace": "default",
                    "cluster": "test",
                    "type": "TLS",
                    "issuer": "test-issuer"
                }
                """;

        mockMvc.perform(post("/api/k8s-certs/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("cert-min"));
    }

    private K8sCertVO buildCert(String id, String name, CertType type, CertStatus status) {
        K8sCertVO cert = K8sCertVO.builder()
                .name(name)
                .namespace("mq-system")
                .cluster("prod-cluster")
                .type(type)
                .issuer("letsencrypt")
                .notBefore(LocalDateTime.of(2025, 1, 1, 0, 0))
                .notAfter(LocalDateTime.of(2026, 1, 1, 0, 0))
                .status(status)
                .daysRemaining(180)
                .san(List.of("example.com"))
                .build();
        cert.setId(id);
        return cert;
    }
}
