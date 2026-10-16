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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cluster list's wire contract (docs/api-spec.md, ClusterInfo) is the fields asserted here. The
 * view declares two bookkeeping columns of its own, and nothing ever fills them - every cluster the
 * console lists is discovered from the NameServer, not read from a registry row - so without the pin
 * they reach the wire as an always-null gmtCreate / gmtModified pair that the contract does not have.
 */
class ClusterWireContractTest {

    private static final Set<String> DOCUMENTED_FIELDS = Set.of(
            "id", "name", "nsClusterName", "type", "endpoint", "status", "version", "brokers", "proxies",
            "nameServers", "config", "topicCount", "groupCount", "tpsHistory");

    @Test
    void clusterSerializesExactlyTheDocumentedFieldsTest() throws Exception {
        ClusterVO cluster = ClusterVO.builder()
                .id("DefaultCluster")
                .name("DefaultCluster")
                .endpoint("127.0.0.1:9876")
                .build();

        JsonNode wire = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(cluster);
        Set<String> keys = new TreeSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = wire.fields(); fields.hasNext(); ) {
            keys.add(fields.next().getKey());
        }
        assertThat(keys).isEqualTo(new TreeSet<>(DOCUMENTED_FIELDS));
    }
}
