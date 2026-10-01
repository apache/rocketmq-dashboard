/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rmq_studio_session")
public class RmqStudioSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime lastSeenAt;

    /** Client address the session was issued to; first X-Forwarded-For hop behind a proxy. */
    private String clientIp;

    /** User-Agent of the client the session was issued to, truncated to the column length. */
    private String userAgent;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
