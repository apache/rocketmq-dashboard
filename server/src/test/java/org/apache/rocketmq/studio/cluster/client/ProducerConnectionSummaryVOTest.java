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

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerConnectionSummaryVOTest {

    @Test
    void fromShouldMarkEmptyConnectionsUnavailable() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(List.of());

        assertThat(summary.getTotalConnections()).isZero();
        assertThat(summary.getReadiness()).isEqualTo(ProducerConnectionSummaryVO.UNAVAILABLE);
        assertThat(summary.getWarnings()).containsExactly(ProducerConnectionSummaryVO.NO_CONNECTIONS);
    }

    @Test
    void fromShouldSkipNullConnectionEntries() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(Arrays.asList(
                null, connection("producer-a", "10.0.0.1:38888", "Java", "5.1.0")));

        assertThat(summary.getTotalConnections()).isEqualTo(1);
        assertThat(summary.getReadiness()).isEqualTo(ProducerConnectionSummaryVO.READY);
    }

    @Test
    void fromShouldReportMixedVersionsAndDuplicateClients() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(List.of(
                connection("producer-a", "10.0.0.1:38888", "Java", "5.1.0"),
                connection("producer-a", "10.0.0.2:38888", "Java", "5.1.0"),
                connection("producer-b", "10.0.0.3:38888", "Go", "5.2.0")));

        assertThat(summary.getTotalConnections()).isEqualTo(3);
        assertThat(summary.getUniqueClientCount()).isEqualTo(2);
        assertThat(summary.getUniqueAddressCount()).isEqualTo(3);
        assertThat(summary.getLanguages())
                .extracting(ProducerConnectionSummaryItemVO::getValue)
                .containsExactly("Java", "Go");
        assertThat(summary.getVersions())
                .extracting(ProducerConnectionSummaryItemVO::getValue)
                .containsExactly("5.1.0", "5.2.0");
        assertThat(summary.getDuplicateClientIds()).containsExactly("producer-a");
        assertThat(summary.getWarnings()).containsExactly(
                ProducerConnectionSummaryVO.DUPLICATE_CLIENT_ID,
                ProducerConnectionSummaryVO.MIXED_CLIENT_VERSION);
        assertThat(summary.getReadiness()).isEqualTo(ProducerConnectionSummaryVO.WARNING);
    }

    @Test
    void fromShouldWarnWhenConnectionMetadataIsIncomplete() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(List.of(
                connection("producer-a", "", null, "5.1.0")));

        assertThat(summary.getReadiness()).isEqualTo(ProducerConnectionSummaryVO.WARNING);
        assertThat(summary.getWarnings()).containsExactly(
                ProducerConnectionSummaryVO.INCOMPLETE_CLIENT_METADATA);
        assertThat(summary.getLanguages())
                .extracting(ProducerConnectionSummaryItemVO::getValue)
                .containsExactly("UNKNOWN");
    }

    @Test
    void fromShouldHandleANullConnectionList() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(null);

        assertThat(summary.getTotalConnections()).isZero();
        assertThat(summary.getUniqueClientCount()).isZero();
        assertThat(summary.getReadiness()).isEqualTo(ProducerConnectionSummaryVO.UNAVAILABLE);
        assertThat(summary.getWarnings()).containsExactly(ProducerConnectionSummaryVO.NO_CONNECTIONS);
    }

    @Test
    void fromShouldTreatTheLiteralNullStringAsMissingMetadata() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(List.of(
                connection("null", "10.0.0.1:38888", "Java", "5.1.0")));

        assertThat(summary.getTotalConnections()).isEqualTo(1);
        assertThat(summary.getUniqueClientCount()).isZero();
        assertThat(summary.getReadiness()).isEqualTo(ProducerConnectionSummaryVO.WARNING);
        assertThat(summary.getWarnings()).containsExactly(
                ProducerConnectionSummaryVO.INCOMPLETE_CLIENT_METADATA);
    }

    @Test
    void distributionShouldTrimValuesAndSortByCountThenName() {
        ProducerConnectionSummaryVO summary = ProducerConnectionSummaryVO.from(List.of(
                connection("producer-a", "10.0.0.1:38888", " Go ", "5.2.0"),
                connection("producer-b", "10.0.0.2:38888", "Go", "5.1.0"),
                connection("producer-c", "10.0.0.3:38888", "Java", "5.1.0")));

        // " Go " and "Go" merge into one dimension; counts order Go(2) before Java(1),
        // and the two single-version entries order alphabetically on a count tie.
        assertThat(summary.getLanguages())
                .extracting(ProducerConnectionSummaryItemVO::getValue)
                .containsExactly("Go", "Java");
        assertThat(summary.getLanguages())
                .extracting(ProducerConnectionSummaryItemVO::getCount)
                .containsExactly(2L, 1L);
        assertThat(summary.getVersions())
                .extracting(ProducerConnectionSummaryItemVO::getValue)
                .containsExactly("5.1.0", "5.2.0");
    }

    private ProducerConnectionVO connection(String clientId, String address, String language, String version) {
        return ProducerConnectionVO.builder()
                .clientId(clientId)
                .clientAddr(address)
                .language(language)
                .versionDesc(version)
                .build();
    }
}
