/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

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
@RequestMapping("/api/brokers/read-ahead")
public class BrokerReadAheadController {
    private final BrokerReadAheadService service;

    @GetMapping
    public Result<BrokerReadAheadService.Snapshot> inspect(@RequestParam String instanceId,
            @RequestParam String brokerName, @RequestParam String address) {
        return Result.ok(service.inspect(instanceId, brokerName, address));
    }

    @PostMapping
    public Result<BrokerReadAheadService.Receipt> apply(@RequestBody BrokerReadAheadService.Request request) {
        return Result.ok(service.apply(request));
    }
}
