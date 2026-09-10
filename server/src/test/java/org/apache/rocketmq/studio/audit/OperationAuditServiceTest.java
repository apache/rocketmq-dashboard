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

package org.apache.rocketmq.studio.audit;

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class OperationAuditServiceTest {

    @Mock
    private RmqOperationAuditMapper auditMapper;

    @AfterEach
    void clearAuthenticatedUser() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void recordShouldCaptureAuthenticatedOperator() {
        OperationAuditService service = new OperationAuditService(auditMapper);
        AuthenticatedUserContext.setUsername("operator-user");

        service.record("CREATE", "TOPIC", "topic-a", "cluster-a", "created topic",
                "SUCCESS", null);

        ArgumentCaptor<RmqOperationAudit> captor = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo("operator-user");
    }

    @Test
    void recordShouldUseSystemOperatorWithoutAuthenticatedRequest() {
        OperationAuditService service = new OperationAuditService(auditMapper);

        service.record("CREATE", "TOPIC", "topic-a", "cluster-a", "created topic",
                "SUCCESS", null);

        ArgumentCaptor<RmqOperationAudit> captor = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperator())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
    }

    @Test
    void recordShouldNotPropagateAuditPersistenceFailures() {
        OperationAuditService service = new OperationAuditService(auditMapper);
        doThrow(new IllegalStateException("audit database unavailable"))
                .when(auditMapper).insert(any(RmqOperationAudit.class));

        assertThatCode(() -> service.record("DIRECT_CONSUME_MESSAGE", "MESSAGE", "msg-1",
                "cluster-a", "result=SUCCESS", "SUCCESS", null))
                .doesNotThrowAnyException();
    }

    @Test
    void recordShouldPersistEveryProvidedField() {
        OperationAuditService service = new OperationAuditService(auditMapper);
        AuthenticatedUserContext.setUsername("ops-bot");

        service.record("UPDATE", "CONSUMER_GROUP", "group-a", "cluster-a",
                "updated read/write perm", "SUCCESS", null);

        ArgumentCaptor<RmqOperationAudit> captor = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(auditMapper).insert(captor.capture());
        RmqOperationAudit audit = captor.getValue();
        assertThat(audit.getOperation()).isEqualTo("UPDATE");
        assertThat(audit.getResourceType()).isEqualTo("CONSUMER_GROUP");
        assertThat(audit.getResourceName()).isEqualTo("group-a");
        assertThat(audit.getClusterId()).isEqualTo("cluster-a");
        assertThat(audit.getDetail()).isEqualTo("updated read/write perm");
        assertThat(audit.getResult()).isEqualTo("SUCCESS");
        assertThat(audit.getErrorMessage()).isNull();
        assertThat(audit.getOperator()).isEqualTo("ops-bot");
        assertThat(audit.getGmtCreate()).isNotNull();
        assertThat(audit.getGmtModified()).isEqualTo(audit.getGmtCreate());
    }

    @Test
    void recordShouldCarryFailureReasonAndTolerateNullOptionalFields() {
        OperationAuditService service = new OperationAuditService(auditMapper);

        service.record("DIRECT_CONSUME_MESSAGE", "MESSAGE", null, null,
                "consumed 10 messages", "FAILED", "broker session timed out");

        ArgumentCaptor<RmqOperationAudit> captor = ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(auditMapper).insert(captor.capture());
        RmqOperationAudit audit = captor.getValue();
        assertThat(audit.getOperation()).isEqualTo("DIRECT_CONSUME_MESSAGE");
        assertThat(audit.getResourceType()).isEqualTo("MESSAGE");
        assertThat(audit.getResourceName()).isNull();
        assertThat(audit.getClusterId()).isNull();
        assertThat(audit.getResult()).isEqualTo("FAILED");
        assertThat(audit.getErrorMessage()).isEqualTo("broker session timed out");
        assertThat(audit.getOperator()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(audit.getGmtCreate()).isNotNull();
        assertThat(audit.getGmtModified()).isEqualTo(audit.getGmtCreate());
    }
}
