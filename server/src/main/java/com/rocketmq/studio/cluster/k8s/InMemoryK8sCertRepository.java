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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InMemoryK8sCertRepository implements K8sCertRepository {

    private final Map<String, K8sCertVO> store = new ConcurrentHashMap<>();

    public InMemoryK8sCertRepository() {
        initStubData();
    }

    @Override
    public List<K8sCertVO> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<K8sCertVO> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public K8sCertVO save(K8sCertVO cert) {
        store.put(cert.getId(), cert);
        log.info("Saved certificate: {} (id={})", cert.getName(), cert.getId());
        return cert;
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
        log.info("Deleted certificate: {}", id);
    }

    private void initStubData() {
        LocalDateTime now = LocalDateTime.now();

        K8sCertVO cert1 = K8sCertVO.builder()
                .name("rmq-proxy-tls")
                .namespace("rocketmq")
                .cluster("rmq-cluster-prod")
                .type(CertType.TLS)
                .issuer("letsencrypt-prod")
                .notBefore(now.minusDays(30))
                .notAfter(now.plusDays(60))
                .status(CertStatus.valid)
                .daysRemaining(60)
                .san(List.of("proxy.rmq.local", "*.proxy.rmq.local"))
                .build();
        cert1.setId("cert-001");
        cert1.setCreatedAt(now.minusDays(30));
        cert1.setUpdatedAt(now.minusDays(30));

        K8sCertVO cert2 = K8sCertVO.builder()
                .name("rmq-broker-mtls")
                .namespace("rocketmq")
                .cluster("rmq-cluster-prod")
                .type(CertType.mTLS)
                .issuer("internal-ca")
                .notBefore(now.minusDays(350))
                .notAfter(now.plusDays(15))
                .status(CertStatus.expiring)
                .daysRemaining(15)
                .san(List.of("broker-a.rmq.local", "broker-b.rmq.local"))
                .build();
        cert2.setId("cert-002");
        cert2.setCreatedAt(now.minusDays(350));
        cert2.setUpdatedAt(now.minusDays(350));

        K8sCertVO cert3 = K8sCertVO.builder()
                .name("rmq-staging-sa")
                .namespace("rocketmq-staging")
                .cluster("rmq-cluster-staging")
                .type(CertType.ServiceAccount)
                .issuer("k8s-self-signed")
                .notBefore(now.minusDays(400))
                .notAfter(now.minusDays(35))
                .status(CertStatus.expired)
                .daysRemaining(-35)
                .san(List.of("sa.rmq-staging.local"))
                .build();
        cert3.setId("cert-003");
        cert3.setCreatedAt(now.minusDays(400));
        cert3.setUpdatedAt(now.minusDays(400));

        store.put(cert1.getId(), cert1);
        store.put(cert2.getId(), cert2);
        store.put(cert3.getId(), cert3);
    }
}
