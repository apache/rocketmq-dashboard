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

/**
 * One persisted timeline entry (rmq_ai_event). {@code payload} holds a serialised
 * {@code TimelineEvent}; tool output is capped before it reaches this row.
 *
 * <p>{@code conversationId} and {@code turn} are deliberate denormalisations so the whole
 * conversation timeline is one filtered query with no join.
 */
@Data
@TableName("rmq_ai_event")
public class RmqAiEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long runId;
    private Integer turn;

    /** Strictly increasing within a conversation; also the reconnect cursor. */
    private Integer seq;

    private String type;
    private String payload;

    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
}
