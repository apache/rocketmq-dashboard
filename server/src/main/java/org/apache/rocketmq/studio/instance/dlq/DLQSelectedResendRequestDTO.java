/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file except in compliance with the License.
 */
package org.apache.rocketmq.studio.instance.dlq;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class DLQSelectedResendRequestDTO {
    @NotBlank(message = "instanceId is required")
    private String instanceId;
    @NotBlank(message = "groupName is required")
    private String groupName;
    private Long startTime;
    private Long endTime;
    private String targetTopic;
    @Valid
    @NotEmpty(message = "messages is required")
    private List<DLQMessageRefDTO> messages;
}
