/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.dlq;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DLQMessagePageVO {
    List<DLQMessageVO> items;
    long total;
    int page;
    int size;
    boolean scanIncomplete;
    int failedQueueCount;
}
