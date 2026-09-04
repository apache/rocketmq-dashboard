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

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceRecordTest {

    @Mock
    private AuditRepository auditRepository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditRepository);
    }

    @AfterEach
    void clearUser() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void recordsOperationWithAuthenticatedOperator() {
        AuthenticatedUserContext.setUsername("alice");

        service.record("CREATE", "Topic", "orders", "cluster-a", "created topic", "SUCCESS");

        ArgumentCaptor<AuditRecordVO> captor = ArgumentCaptor.forClass(AuditRecordVO.class);
        verify(auditRepository).save(captor.capture());
        AuditRecordVO record = captor.getValue();
        assertThat(record.getOperationType()).isEqualTo("CREATE");
        assertThat(record.getResourceType()).isEqualTo("Topic");
        assertThat(record.getTarget()).isEqualTo("orders");
        assertThat(record.getClusterId()).isEqualTo("cluster-a");
        assertThat(record.getOperator()).isEqualTo("alice");
        assertThat(record.getTimestamp()).isNotNull();
    }

    @Test
    void fallsBackToSystemOperatorWhenUnauthenticated() {
        service.record("DELETE", "Group", "cg-1", null, "removed group", "SUCCESS");

        ArgumentCaptor<AuditRecordVO> captor = ArgumentCaptor.forClass(AuditRecordVO.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(captor.getValue().getClusterId()).isNull();
    }
}
