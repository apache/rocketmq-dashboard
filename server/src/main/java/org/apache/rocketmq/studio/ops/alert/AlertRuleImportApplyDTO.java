/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertRuleImportApplyDTO {
    @NotNull(message = "transfer is required")
    @Valid
    private AlertRuleTransferDTO transfer;

    @NotNull(message = "strategy is required")
    private AlertRuleImportConflictStrategy strategy;
}
