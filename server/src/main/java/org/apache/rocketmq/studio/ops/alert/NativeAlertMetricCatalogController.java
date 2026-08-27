/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/native-alert-metrics")
@RequiredArgsConstructor
public class NativeAlertMetricCatalogController {
    private final NativeAlertMetricCatalogService catalogService;

    @GetMapping
    public Result<List<NativeAlertMetricInfo>> list(@RequestParam String instanceId,
            @RequestParam AlertDomain domain) {
        return Result.ok(catalogService.list(instanceId, domain));
    }
}
