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

package org.apache.rocketmq.studio.instance;

import org.apache.rocketmq.studio.common.domain.BaseEntity;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InstanceVO extends BaseEntity {
    private String name;
    private String remark;
    private InstanceType type;
    private String endpoint;
    private InstanceVendor vendor;
    private String cloudInstanceId;
    private Long credentialId;
    private String adminCredentialRef;
    private String regionId;
    private String regionName;
    private int topicCount;
    private int consumerGroupCount;
    /**
     * Whether {@link #topicCount} and {@link #consumerGroupCount} were measured for this value.
     * Counts are not columns on the row: they are resolved per request, so only the calls that
     * actually resolve them ({@code listInstances}) can claim them. Defaulting to {@code true} made
     * every value that was never measured - the create and update responses, say - report the
     * read-back zeros as a measured fact, which the field's contract in {@code docs/api-spec.md}
     * forbids.
     */
    @Builder.Default
    private boolean resourceCountsAvailable = false;
}
