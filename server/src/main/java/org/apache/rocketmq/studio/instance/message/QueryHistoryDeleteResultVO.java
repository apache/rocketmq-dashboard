/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.Builder;
import lombok.Value;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Counts returned after an operator removes query history. */
@Value
@Builder
public class QueryHistoryDeleteResultVO {
    int messageQueries;
    int traceQueries;

    @JsonProperty("total")
    public int total() {
        return messageQueries + traceQueries;
    }
}
