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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.rocketmq.studio.common.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
// The feed's contract (docs/api-spec.md AuditRecord) is exactly the ten fields below. The base class
// carries the bookkeeping columns, and the audit query never selects them, so without this they reach
// the wire as an always-null gmtCreate / gmtModified pair that neither the spec nor the console reads.
@JsonIgnoreProperties({"gmtCreate", "gmtModified"})
public class AuditRecordVO extends BaseEntity {
    private LocalDateTime timestamp;
    private String operator;
    private String operationType;
    private String resourceType;
    private String target;
    private String clusterId;
    private String detail;
    private String result;
    private String errorMessage;
}
