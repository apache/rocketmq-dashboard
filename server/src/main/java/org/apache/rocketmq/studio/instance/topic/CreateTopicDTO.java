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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.provider.LiteTopicProvider;

@Data
public class CreateTopicDTO {
    @NotBlank(message = "name is required")
    private String name;
    private String namespace;
    private String clusterId;
    private TopicType type;
    @PositiveOrZero(message = "writeQueues must be zero or positive")
    private Integer writeQueues;
    @PositiveOrZero(message = "readQueues must be zero or positive")
    private Integer readQueues;
    private TopicPerm perm;
    private String remark;

    /**
     * TTL of a new Lite parent topic in minutes — only accepted with {@code type=LITE} and only on
     * create; the generic update path never edits it. Omitting it falls back to
     * {@link LiteTopicProvider#DEFAULT_LITE_TTL_MINUTES} instead of the broker's own {@code -1}
     * (never expire) attribute default, which is also what the console dialog prefills.
     *
     * <p>Unit divergence on the same resource: this create field is in minutes while
     * {@code POST /api/liteTopic/extendTTL} takes {@code newTTL} in milliseconds. Both are
     * documented in {@code docs/api-spec.md} (5.3 and 5.11).
     */
    @Min(value = 1, message = "liteTtlMinutes must be between 1 and 43200")
    @Max(value = LiteTopicProvider.MAX_LITE_TTL_MINUTES, message = "liteTtlMinutes must be between 1 and 43200")
    private Integer liteTtlMinutes;

    private String instanceId;

    public TopicVO toTopicVO() {
        TopicVO topic = new TopicVO();
        topic.setName(name);
        topic.setNamespace(namespace);
        topic.setClusterId(clusterId);
        topic.setType(type);
        if (writeQueues != null) {
            topic.setWriteQueues(writeQueues);
        }
        if (readQueues != null) {
            topic.setReadQueues(readQueues);
        }
        topic.setPerm(perm);
        topic.setRemark(remark);
        topic.setLiteTtlMinutes(liteTtlMinutes);
        return topic;
    }
}
