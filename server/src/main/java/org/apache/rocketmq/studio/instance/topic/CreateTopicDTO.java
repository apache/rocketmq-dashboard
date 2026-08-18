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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;

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
        return topic;
    }
}
