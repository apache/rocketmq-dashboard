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

public record AuditItem(
        Long id,
        String timestamp,
        String operator,
        String operationType,
        String resourceType,
        String target,
        String clusterId,
        String detail,
        String result,
        String errorMessage) {

    public static AuditItem from(AuditRecordVO record) {
        return new AuditItem(
                record.getId(),
                record.getTimestamp() == null ? null : record.getTimestamp().toString(),
                record.getOperator(),
                record.getOperationType(),
                record.getResourceType(),
                record.getTarget(),
                record.getClusterId(),
                record.getDetail(),
                record.getResult(),
                record.getErrorMessage());
    }
}
