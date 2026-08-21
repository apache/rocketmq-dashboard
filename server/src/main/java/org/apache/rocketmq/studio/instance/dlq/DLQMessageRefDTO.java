/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.dlq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DLQMessageRefDTO {
    @NotBlank(message = "msgId is required")
    private String msgId;
    private int queueId;
    @PositiveOrZero(message = "offset must not be negative")
    private long offset;
}
