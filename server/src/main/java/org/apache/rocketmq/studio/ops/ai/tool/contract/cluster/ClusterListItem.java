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
package org.apache.rocketmq.studio.ops.ai.tool.contract.cluster;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClusterListItem(
        String id,
        String name,
        ClusterType type,
        ClusterStatus status,
        String version) {

    public static ClusterListItem from(ClusterVO cluster) {
        return new ClusterListItem(
                cluster.getId(),
                cluster.getName(),
                cluster.getType(),
                cluster.getStatus(),
                cluster.getVersion());
    }
}
