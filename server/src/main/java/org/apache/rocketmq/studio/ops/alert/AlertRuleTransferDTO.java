/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Portable native-alert rule document. IDs and runtime state are deliberately excluded. */
@Data
public class AlertRuleTransferDTO {
    public static final int VERSION = 1;

    @NotNull(message = "version is required")
    private Integer version;

    @NotNull(message = "domain is required")
    private AlertDomain domain;

    @NotEmpty(message = "rules must not be empty")
    private List<@NotNull(message = "rule must not be null") @Valid AlertRuleRequestDTO> rules;
}
