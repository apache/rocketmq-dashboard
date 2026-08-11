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
public class TraceQueryHistoryVO {
    private Long id;
    private String msgId;
    private String topic;
    private int nodeCount;
    private int consumerCount;
    private String clusterId;
    private String queriedBy;
    private LocalDateTime queriedAt;
}
