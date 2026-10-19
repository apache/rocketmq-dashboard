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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the audit record view of {@link AuditRecordVO}: one audit-trail entry as the feed
 * renders it - the operator, the operation against a typed resource, the cluster routing and
 * the outcome with its error detail.
 */
class AuditRecordVOTest {

    @Test
    void everyFieldRoundTrips() {
        AuditRecordVO record = new AuditRecordVO();
        record.setId(7L);
        record.setTimestamp(LocalDateTime.of(2026, 1, 2, 3, 4));
        record.setOperator("alice");
        record.setOperationType("CREATE_TOPIC");
        record.setResourceType("TOPIC");
        record.setTarget("orders");
        record.setClusterId("cluster-1");
        record.setDetail("created 8 queues");
        record.setResult("SUCCESS");
        record.setErrorMessage(null);

        assertThat(record.getId()).isEqualTo(7L);
        assertThat(record.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(record.getOperator()).isEqualTo("alice");
        assertThat(record.getOperationType()).isEqualTo("CREATE_TOPIC");
        assertThat(record.getResourceType()).isEqualTo("TOPIC");
        assertThat(record.getTarget()).isEqualTo("orders");
        assertThat(record.getClusterId()).isEqualTo("cluster-1");
        assertThat(record.getDetail()).isEqualTo("created 8 queues");
        assertThat(record.getResult()).isEqualTo("SUCCESS");
        assertThat(record.getErrorMessage()).isNull();
    }

    @Test
    void aFailedRecordCarriesItsError() {
        AuditRecordVO record = new AuditRecordVO();
        record.setOperationType("DELETE_TOPIC");
        record.setResult("FAILED");
        record.setErrorMessage("topic not found: orders");

        assertThat(record.getResult()).isEqualTo("FAILED");
        assertThat(record.getErrorMessage()).isEqualTo("topic not found: orders");
    }
}
