/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;

import java.util.Map;

public final class TestToolExecutionContexts {

    private TestToolExecutionContexts() {
    }

    public static ToolExecutionContext context(String instanceId) {
        return context(instanceId, Map.of("cluster", instanceId));
    }

    public static ToolExecutionContext context(
            String instanceId, Map<String, Object> input) {
        return ToolExecutionContext.of(instanceId, null, input);
    }
}
