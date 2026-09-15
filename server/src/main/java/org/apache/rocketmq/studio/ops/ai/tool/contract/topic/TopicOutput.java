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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.topic.TopicVO;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopicOutput(
        String name,
        String namespace,
        String cluster,
        String type,
        int writeQueues,
        int readQueues,
        String perm,
        String remark) {

    public static TopicOutput from(TopicVO topic) {
        return new TopicOutput(
                topic.getName(),
                topic.getNamespace(),
                topic.getClusterId(),
                topic.getType() != null ? topic.getType().name() : null,
                topic.getWriteQueues(),
                topic.getReadQueues(),
                topic.getPerm() != null ? topic.getPerm().name() : null,
                topic.getRemark());
    }
}
