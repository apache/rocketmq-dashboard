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
package org.apache.rocketmq.studio.ops.ai.tool.contract.ops;

import org.apache.rocketmq.studio.ops.audit.AuditRecordVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AuditItem}: the tool-side projection of an audit row. The timestamp translation is
 * the subtle half - the storage layer speaks LocalDateTime, the tool contract speaks a string, and
 * a null timestamp must survive as null rather than crash the whole audit listing.
 */
class AuditItemTest {

    @Test
    void mapsEveryAuditFieldThrough() {
        AuditRecordVO record = new AuditRecordVO();
        record.setId(42L);
        record.setTimestamp(LocalDateTime.of(2026, 10, 8, 12, 30));
        record.setOperator("alice");
        record.setOperationType("CREATE_TOPIC");
        record.setResourceType("TOPIC");
        record.setTarget("orders");
        record.setClusterId("instance-a");
        record.setDetail("created 8 queues");
        record.setResult("SUCCESS");
        record.setErrorMessage(null);

        AuditItem item = AuditItem.from(record);

        assertThat(item.id()).isEqualTo(42L);
        assertThat(item.timestamp()).isEqualTo("2026-10-08T12:30");
        assertThat(item.operator()).isEqualTo("alice");
        assertThat(item.operationType()).isEqualTo("CREATE_TOPIC");
        assertThat(item.resourceType()).isEqualTo("TOPIC");
        assertThat(item.target()).isEqualTo("orders");
        assertThat(item.clusterId()).isEqualTo("instance-a");
        assertThat(item.detail()).isEqualTo("created 8 queues");
        assertThat(item.result()).isEqualTo("SUCCESS");
        assertThat(item.errorMessage()).isNull();
    }

    @Test
    void aNullTimestampSurvivesAsNull() {
        // an audit row written before a schema addition must not cost the
        // whole listing its projection
        AuditRecordVO record = new AuditRecordVO();
        record.setId(7L);
        record.setOperator("bob");

        AuditItem item = AuditItem.from(record);

        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.timestamp()).isNull();
        assertThat(item.operator()).isEqualTo("bob");
    }

    @Test
    void aFailureCarriesItsErrorMessage() {
        AuditRecordVO record = new AuditRecordVO();
        record.setResult("FAILED");
        record.setErrorMessage("broker unreachable");

        AuditItem item = AuditItem.from(record);

        assertThat(item.result()).isEqualTo("FAILED");
        assertThat(item.errorMessage()).isEqualTo("broker unreachable");
    }
}
