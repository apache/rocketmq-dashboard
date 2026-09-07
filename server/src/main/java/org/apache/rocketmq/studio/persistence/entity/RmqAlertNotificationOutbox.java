/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
*/
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rmq_alert_notification_outbox")
public class RmqAlertNotificationOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertId;
    private String channel;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime sendingStartedAt;
    private String claimToken;
    private String lastError;
    private String messageContent;
    private LocalDateTime deliveredAt;
    private LocalDateTime gmtModified;
}
