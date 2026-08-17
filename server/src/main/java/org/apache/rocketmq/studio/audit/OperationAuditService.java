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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class OperationAuditService {

    private final RmqOperationAuditMapper auditMapper;

    public OperationAuditService(RmqOperationAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    public void record(String operation, String resourceType, String resourceName,
                       String clusterId, String detail, String result, String errorMessage) {
        RmqOperationAudit audit = new RmqOperationAudit();
        audit.setOperation(operation);
        audit.setResourceType(resourceType);
        audit.setResourceName(resourceName);
        audit.setClusterId(clusterId);
        audit.setDetail(detail);
        audit.setResult(result);
        audit.setErrorMessage(errorMessage);
        audit.setOperator(AuthenticatedUserContext.currentUsernameOrSystem());
        LocalDateTime now = LocalDateTime.now();
        audit.setGmtCreate(now);
        audit.setGmtModified(now);
        auditMapper.insert(audit);
        log.debug("Audit recorded: {} {} {}", operation, resourceType, resourceName);
    }
}
