/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageQueryHistoryVO {
    private Long id;
    private String queryType;
    private String topic;
    private String msgId;
    private String tag;
    private String messageKey;
    private Long startTime;
    private Long endTime;
    private int resultCount;
    private String clusterId;
    private String queriedBy;
    private LocalDateTime queriedAt;
}
