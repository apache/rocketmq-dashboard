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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the registry entry view of {@link NameserverRegistryVO}: a registered NameServer
 * cluster - static address list or Kubernetes-discovered - round-trips through the builder
 * with its status and description.
 */
class NameserverRegistryVOTest {

    @Test
    void aStaticRegistryEntryRoundTrips() {
        NameserverRegistryVO entry = NameserverRegistryVO.builder()
                .id(7L)
                .name("ns-prod")
                .namesrvAddr("10.0.0.1:9876")
                .status("ACTIVE")
                .description("static list")
                .gmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();

        assertThat(entry.getId()).isEqualTo(7L);
        assertThat(entry.getName()).isEqualTo("ns-prod");
        assertThat(entry.getNamesrvAddr()).isEqualTo("10.0.0.1:9876");
        assertThat(entry.getStatus()).isEqualTo("ACTIVE");
        assertThat(entry.getDescription()).isEqualTo("static list");
        assertThat(entry.getK8sNamespace()).isNull();
        assertThat(entry.getK8sId()).isNull();
    }

    @Test
    void aKubernetesEntryCarriesItsDiscoveryFields() {
        NameserverRegistryVO entry = NameserverRegistryVO.builder()
                .name("ns-k8s")
                .k8sNamespace("rocketmq")
                .k8sId("statefulset/rocketmq-nameserver")
                .build();

        assertThat(entry.getK8sNamespace()).isEqualTo("rocketmq");
        assertThat(entry.getK8sId()).isEqualTo("statefulset/rocketmq-nameserver");
        assertThat(entry.getNamesrvAddr()).isNull();
    }

    @Test
    void aFreshEntryCarriesNoTimestamps() {
        NameserverRegistryVO entry = new NameserverRegistryVO();
        assertThat(entry.getGmtCreate()).isNull();
        assertThat(entry.getStatus()).isNull();
    }
}
