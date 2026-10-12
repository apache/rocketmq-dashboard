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
package org.apache.rocketmq.studio.ops.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the audit filter options of {@link AuditFilterOptionsVO}: the four vocabularies the
 * filter dropdowns offer - operation types, resource types, cluster ids and results - travel
 * together, and a fresh options set offers no dropdowns at all.
 */
class AuditFilterOptionsVOTest {

    @Test
    void everyVocabularyRoundTripsThroughTheBuilder() {
        AuditFilterOptionsVO options = AuditFilterOptionsVO.builder()
                .operationTypes(List.of("CREATE_TOPIC", "DELETE_TOPIC"))
                .resourceTypes(List.of("TOPIC", "GROUP"))
                .clusterIds(List.of("cluster-1"))
                .results(List.of("SUCCESS", "FAILED"))
                .build();

        assertThat(options.getOperationTypes()).containsExactly("CREATE_TOPIC", "DELETE_TOPIC");
        assertThat(options.getResourceTypes()).containsExactly("TOPIC", "GROUP");
        assertThat(options.getClusterIds()).containsExactly("cluster-1");
        assertThat(options.getResults()).containsExactly("SUCCESS", "FAILED");
    }

    @Test
    void aFreshOptionsSetOffersNothing() {
        AuditFilterOptionsVO options = new AuditFilterOptionsVO();
        assertThat(options.getOperationTypes()).isNull();
        assertThat(options.getResourceTypes()).isNull();
        assertThat(options.getClusterIds()).isNull();
        assertThat(options.getResults()).isNull();
    }
}
