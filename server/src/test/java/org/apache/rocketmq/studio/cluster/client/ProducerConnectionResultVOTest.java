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
package org.apache.rocketmq.studio.cluster.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the scan result of {@link ProducerConnectionResultVO}: the convenience constructor
 * assumes a complete scan with no failures, the full constructor normalises null lists to empty
 * and defensively copies them, and the summary is derived from the carried connections.
 */
class ProducerConnectionResultVOTest {

    private ProducerConnectionVO connection(String clientId, String addr, String language, String version) {
        return ProducerConnectionVO.builder()
                .clientId(clientId)
                .clientAddr(addr)
                .topic("topic-1")
                .producerGroup("group-1")
                .language(language)
                .versionDesc(version)
                .build();
    }

    @Test
    void theConvenienceConstructorAssumesACompleteScan() {
        ProducerConnectionResultVO result = new ProducerConnectionResultVO(
                List.of(connection("client-1", "10.0.0.1:10920", "JAVA", "5.5.0")));

        assertThat(result.isComplete()).isTrue();
        assertThat(result.getFailedBrokers()).isEmpty();
        assertThat(result.getFailedProducerGroups()).isEmpty();
        assertThat(result.getSummary().getTotalConnections()).isEqualTo(1);
        assertThat(result.getSummary().getUniqueClientCount()).isEqualTo(1);
        assertThat(result.getSummary().getWarnings()).isEmpty();
    }

    @Test
    void aNullConnectionSetNormalisesToEmptyAndWarns() {
        ProducerConnectionResultVO result = new ProducerConnectionResultVO(null, true, null, null);

        assertThat(result.getConnectionSet()).isEmpty();
        assertThat(result.getFailedBrokers()).isEmpty();
        assertThat(result.getFailedProducerGroups()).isEmpty();
        assertThat(result.getSummary().getTotalConnections()).isZero();
        assertThat(result.getSummary().getWarnings())
                .contains(ProducerConnectionSummaryVO.NO_CONNECTIONS);
    }

    @Test
    void anIncompleteScanCarriesItsFailures() {
        ProducerConnectionResultVO result = new ProducerConnectionResultVO(
                List.of(connection("client-1", "10.0.0.1:10920", "JAVA", "5.5.0")),
                false,
                List.of("broker-b"),
                List.of("group-2"));

        assertThat(result.isComplete()).isFalse();
        assertThat(result.getFailedBrokers()).containsExactly("broker-b");
        assertThat(result.getFailedProducerGroups()).containsExactly("group-2");
        assertThat(result.getSummary().getWarnings())
                .contains(ProducerConnectionSummaryVO.INCOMPLETE_SCAN);
    }

    @Test
    void theConnectionSetIsDefensivelyCopied() {
        List<ProducerConnectionVO> source = new ArrayList<>();
        source.add(connection("client-1", "10.0.0.1:10920", "JAVA", "5.5.0"));

        ProducerConnectionResultVO result = new ProducerConnectionResultVO(source, true, List.of(), List.of());
        source.add(connection("client-2", "10.0.0.2:10920", "JAVA", "5.5.0"));

        assertThat(result.getConnectionSet()).hasSize(1);
        assertThat(result.getSummary().getTotalConnections()).isEqualTo(1);
    }
}
