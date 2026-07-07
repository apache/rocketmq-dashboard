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
package com.rocketmq.studio.instance.topic;

import com.rocketmq.studio.common.domain.BaseEntity;
import com.rocketmq.studio.common.domain.enums.TopicPerm;
import com.rocketmq.studio.common.domain.enums.TopicType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TopicVO extends BaseEntity {
    private String name;
    private String namespace;
    private String clusterId;
    private TopicType type;
    private int writeQueues;
    private int readQueues;
    private TopicPerm perm;
    private long messageCount;
    private double tps;
    private int consumerGroupCount;
    private String remark;
}
