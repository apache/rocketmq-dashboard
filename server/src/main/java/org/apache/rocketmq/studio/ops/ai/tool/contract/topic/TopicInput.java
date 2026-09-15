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

public record TopicInput(
        String cluster,
        String topic,
        String namespace,
        TopicType type,
        Integer writeQueues,
        Integer readQueues,
        TopicPerm perm,
        String remark) {

    private static final int DEFAULT_QUEUE_COUNT = 8;
    private static final TopicType DEFAULT_TYPE = TopicType.NORMAL;
    private static final TopicPerm DEFAULT_PERM = TopicPerm.RW;

    public TopicVO toTopicVO() {
        TopicVO vo = new TopicVO();
        vo.setName(topic);
        vo.setInstanceId(cluster);
        vo.setNamespace(namespace);
        vo.setRemark(remark);
        vo.setWriteQueues(writeQueues != null ? writeQueues : DEFAULT_QUEUE_COUNT);
        vo.setReadQueues(readQueues != null ? readQueues : DEFAULT_QUEUE_COUNT);
        vo.setType(type != null ? type : DEFAULT_TYPE);
        vo.setPerm(perm != null ? perm : DEFAULT_PERM);
        return vo;
    }

    public static TopicInput from(TopicVO topic) {
        return new TopicInput(
                topic.getInstanceId(),
                topic.getName(),
                topic.getNamespace(),
                topic.getType(),
                topic.getWriteQueues(),
                topic.getReadQueues(),
                topic.getPerm(),
                topic.getRemark());
    }

}
