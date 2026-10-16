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
package org.apache.rocketmq.studio.cluster.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cluster view carries no resource counts, because no cluster path measures them:
 * {@code RocketMQClusterProvider} discovers clusters from the NameServer and {@code
 * RealClusterProvider} from the registry table, and neither counts the topics or consumer groups
 * they hold. A count declared here therefore reached every client as a plain {@code 0} that was
 * indistinguishable from a cluster with no resources at all - while the measured per-cluster
 * numbers are served by {@code GET /api/dashboard}, which walks each master broker to collect
 * them. The two counts stay out of the cluster payload and out of {@code docs/api-spec.md};
 * re-adding one means teaching a cluster path to measure it first.
 */
class ClusterResourceCountsTest {

    private static final Set<String> COUNT_FIELDS = Set.of("topicCount", "groupCount");

    @Test
    void clusterViewClaimsNoResourceCountTest() throws Exception {
        ClusterVO cluster = ClusterVO.builder()
                .id("DefaultCluster")
                .name("DefaultCluster")
                .endpoint("127.0.0.1:9876")
                .tpsHistory(List.of(1, 2))
                .build();

        Set<String> keys = serializedFieldNames(cluster);

        assertThat(keys).doesNotContainAnyElementsOf(COUNT_FIELDS);
        // Keeps the assertion above from passing on a payload that serializes nothing at all.
        assertThat(keys).contains("id", "name", "endpoint", "tpsHistory");
    }

    private static Set<String> serializedFieldNames(ClusterVO cluster) throws Exception {
        JsonNode wire = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(cluster);
        Set<String> keys = new TreeSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = wire.fields(); fields.hasNext(); ) {
            keys.add(fields.next().getKey());
        }
        return keys;
    }
}
