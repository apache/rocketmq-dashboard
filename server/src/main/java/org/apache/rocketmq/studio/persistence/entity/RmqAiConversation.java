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

/** AI conversation container (rmq_ai_conversation). */
@Data
@TableName("rmq_ai_conversation")
public class RmqAiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String owner;
    private String engine;
    private String model;
    private String mode;
    private String instanceId;

    /** The upstream agent CLI's own session id, used for {@code --resume} on the next turn. */
    private String runtimeSessionId;

    /** Cached high-water mark only; the authority is {@code MAX(rmq_ai_event.seq)}. */
    private Integer lastSeq;

    private Boolean archived;

    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
}
