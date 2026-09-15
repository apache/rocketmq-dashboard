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
        String instanceId,
        String topicName,
        TopicType type,
        Integer writeQueues,
        Integer readQueues,
        TopicPerm perm,
        String remark) {

    private static final int DEFAULT_QUEUE_COUNT = 8;
    private static final TopicType DEFAULT_TYPE = TopicType.NORMAL;
    private static final TopicPerm DEFAULT_PERM = TopicPerm.RW;

    /**
     * Applies supplied values to a copy of the current configuration, without creation defaults.
     * The topic type is immutable: it is never merged from the input, only the creation path
     * ({@link #toTopicVO()}) honours it.
     */
    public TopicVO mergeWith(TopicVO current) {
        TopicVO merged = new TopicVO();
        BeanUtils.copyProperties(current, merged);
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

    /** Builds a new configuration from the supplied values, filling creation defaults for absent fields. */
    public TopicVO toTopicVO() {
        TopicVO vo = new TopicVO();
        vo.setName(topicName);
        vo.setInstanceId(instanceId);
        vo.setRemark(remark);
        vo.setWriteQueues(writeQueues != null ? writeQueues : DEFAULT_QUEUE_COUNT);
        vo.setReadQueues(readQueues != null ? readQueues : DEFAULT_QUEUE_COUNT);
        vo.setType(type != null ? type : DEFAULT_TYPE);
        vo.setPerm(perm != null ? perm : DEFAULT_PERM);
        return vo;
    }
}
