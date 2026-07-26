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
package com.rocketmq.studio.ops.audit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuditRepositoryTest {

    private final InMemoryAuditRepository repository = new InMemoryAuditRepository();

    @Test
    void findAllShouldSearchAcrossNullableFields() {
        AuditRecordVO missingTextFields = AuditRecordVO.builder()
                .timestamp(LocalDateTime.now())
                .operationType("CREATE")
                .result("SUCCESS")
                .build();
        missingTextFields.setId("record-null-fields");
        AuditRecordVO targetMatch = AuditRecordVO.builder()
                .timestamp(LocalDateTime.now().minusMinutes(1))
                .operator("admin")
                .operationType("UPDATE")
                .target("Topic-Order")
                .detail(null)
                .result("SUCCESS")
                .build();
        targetMatch.setId("record-target-match");

        putRecords(missingTextFields, targetMatch);

        assertThat(repository.findAll("order", null, null, null, null))
                .extracting(AuditRecordVO::getId)
                .containsExactly("record-target-match");
    }

    @Test
    void findAllShouldTreatBlankSearchAsNoSearchFilter() {
        AuditRecordVO record = AuditRecordVO.builder()
                .timestamp(LocalDateTime.now())
                .operationType("DELETE")
                .result("FAILURE")
                .build();
        record.setId("record-blank-search");

        putRecords(record);

        assertThat(repository.findAll("   ", null, null, null, null))
                .extracting(AuditRecordVO::getId)
                .containsExactly("record-blank-search");
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private final void putRecords(AuditRecordVO... records) {
        Map<String, AuditRecordVO> store =
                (Map<String, AuditRecordVO>) ReflectionTestUtils.getField(repository, "records");
        assertThat(store).isNotNull();
        store.clear();
        for (AuditRecordVO record : records) {
            store.put(record.getId(), record);
        }
    }
}
