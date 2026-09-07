/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DirectConsumeMessageResultVO {
    String consumeResult;
    String remark;
    long spentTimeMillis;
    boolean order;
    boolean autoCommit;
}
