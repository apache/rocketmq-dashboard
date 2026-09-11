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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rmq_instance_trace")
public class RmqTraceQuery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String msgId;

    private String topic;

    /**
     * The trace topic selected for the lookup. A null value means that the provider default
     * (normally {@code RMQ_SYS_TRACE_TOPIC}) was used. Keeping this nullable preserves the
     * meaning of history rows written before custom trace-topic support existed.
     */
    private String traceTopic;

    private Integer nodeCount;

    private Integer consumerCount;

    private String clusterId;

    private String queriedBy;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
