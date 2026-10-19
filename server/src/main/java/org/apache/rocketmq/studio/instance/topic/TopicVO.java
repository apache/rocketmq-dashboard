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
package org.apache.rocketmq.studio.instance.topic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.common.domain.BaseEntity;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TopicVO extends BaseEntity {
    private String name;
    private String namespace;
    private String clusterId;
    private String instanceId;
    private TopicType type;
    private int writeQueues;
    private int readQueues;
    private TopicPerm perm;
    private long messageCount;
    private double tps;
    private int consumerGroupCount;
    private String remark;
    /**
     * Create-only TTL for Lite parent topics in minutes ({@code type=LITE}); never populated on
     * read paths and never edited by the generic update path. Kept out of the JSON when unset so a
     * list or detail response does not advertise a TTL the server never read back.
     *
     * <p>Unit note: this create-time field is in <em>minutes</em>, the unit of the broker's
     * {@code lite.topic.expiration} attribute, while {@code POST /api/liteTopic/extendTTL} takes
     * {@code newTTL} in <em>milliseconds</em> and the provider rounds it with
     * {@code clamp(round(millis / 60000), 1, 43200)}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer liteTtlMinutes;
}
