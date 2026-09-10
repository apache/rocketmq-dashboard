/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

public record AlertRuleImportPreviewItemVO(
        int rowNumber,
        Status status,
        String name,
        String metric,
        Long existingRuleId,
        String existingRuleName,
        String error) {

    public enum Status {
        NEW,
        DUPLICATE,
        INVALID
    }
}
