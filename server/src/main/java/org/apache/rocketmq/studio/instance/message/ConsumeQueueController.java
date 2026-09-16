/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConsumeQueueController {
    private final ConsumeQueueService service;

    @GetMapping("/api/messages/consume-queue")
    public Result<ConsumeQueueSnapshot> inspect(@RequestParam String instanceId,
            @RequestParam String topic, @RequestParam String brokerName, @RequestParam int queueId,
            @RequestParam String index, @RequestParam(defaultValue = "16") int count,
            @RequestParam(required = false) String consumerGroup) {
        return Result.ok(service.inspect(instanceId, topic, brokerName, queueId, index, count, consumerGroup));
    }
}
