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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit feed's wire contract (docs/api-spec.md §13.1 AuditRecord) is exactly the ten fields
 * asserted here. The view inherits the bookkeeping columns from BaseEntity, and the audit query
 * never selects them, so without the pin they reach the wire as an always-null gmtCreate /
 * gmtModified pair that neither the spec nor the console knows about.
 */
class AuditRecordWireContractTest {

    private static final Set<String> DOCUMENTED_FIELDS = Set.of(
            "id", "timestamp", "operator", "operationType", "resourceType", "target", "clusterId",
            "detail", "result", "errorMessage");

    @Test
    void auditRecordSerializesExactlyTheDocumentedFieldsTest() throws Exception {
        AuditRecordVO record = AuditRecordVO.builder()
                .timestamp(LocalDateTime.of(2026, 10, 15, 12, 30, 5))
                .operator("system")
                .operationType("CREATE_TOPIC")
                .resourceType("TOPIC")
                .target("orders")
                .clusterId("DefaultCluster")
                .detail("queues=4/4")
                .result("SUCCESS")
                .errorMessage("")
                .build();
        record.setId(7L);

        JsonNode wire = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(record);
        Set<String> keys = new TreeSet<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = wire.fields(); fields.hasNext(); ) {
            keys.add(fields.next().getKey());
        }
        assertThat(keys).isEqualTo(new TreeSet<>(DOCUMENTED_FIELDS));
    }
}
