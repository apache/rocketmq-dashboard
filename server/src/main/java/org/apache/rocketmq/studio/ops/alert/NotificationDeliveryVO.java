/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0.
*/
package org.apache.rocketmq.studio.ops.alert;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

@Value
@Builder
public class NotificationDeliveryVO {
    Long id;
    String channel;
    NotificationOutboxStatus status;
    int attemptCount;
    LocalDateTime nextAttemptAt;
    String lastError;
    LocalDateTime deliveredAt;
}
