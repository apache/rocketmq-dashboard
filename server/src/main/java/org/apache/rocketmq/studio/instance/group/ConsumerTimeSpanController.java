/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConsumerTimeSpanController {
    private final ConsumerTimeSpanService service;

    @GetMapping("/api/consumer-time-spans")
    public Result<ConsumerTimeSpanSnapshot> inspect(@RequestParam String instanceId,
            @RequestParam String topic, @RequestParam String group) {
        return Result.ok(service.inspect(instanceId, topic, group));
    }
}
