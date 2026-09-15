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

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.springframework.beans.BeanUtils;

/** Optional values stay absent; the input schema rejects explicit JSON nulls. */
public record TopicUpdateInput(
        String cluster,
        String topic,
        String namespace,
        TopicType type,
        Integer writeQueues,
        Integer readQueues,
        TopicPerm perm,
        String remark) {

    /** Applies supplied values to a copy of the current configuration, without creation defaults. */
    public TopicVO mergeWith(TopicVO current) {
        TopicVO merged = new TopicVO();
        BeanUtils.copyProperties(current, merged);
        if (namespace != null) {
            merged.setNamespace(namespace);
        }
        if (type != null) {
            merged.setType(type);
        }
        if (writeQueues != null) {
            merged.setWriteQueues(writeQueues);
        }
        if (readQueues != null) {
            merged.setReadQueues(readQueues);
        }
        if (perm != null) {
            merged.setPerm(perm);
        }
        if (remark != null) {
            merged.setRemark(remark);
        }
        return merged;
    }
}
