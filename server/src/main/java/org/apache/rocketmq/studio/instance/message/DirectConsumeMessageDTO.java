/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.instance.message;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DirectConsumeMessageDTO {
    @NotBlank(message = "instanceId is required")
    private String instanceId;
    @NotBlank(message = "topic is required")
    private String topic;
    @NotBlank(message = "msgId is required")
    private String msgId;
    @NotBlank(message = "consumerGroup is required")
    private String consumerGroup;
    @NotBlank(message = "clientId is required")
    private String clientId;
}
