/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consumer-offset-copy")
public class ConsumerOffsetCopyController {
    private final ConsumerOffsetCopyService service;

    @GetMapping
    public Result<ConsumerOffsetCopy.Preview> preview(@RequestParam String instanceId,
            @RequestParam String topic, @RequestParam String sourceGroup, @RequestParam String targetGroup) {
        return Result.ok(service.preview(instanceId, topic, sourceGroup, targetGroup));
    }

    @PostMapping
    public Result<ConsumerOffsetCopy.Receipt> apply(@RequestBody ConsumerOffsetCopy.Request request) {
        return Result.ok(service.apply(request));
    }
}
