/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/query-history/named-messages")
@RequiredArgsConstructor
public class NamedMessageQueryController {
    private final NamedMessageQueryService service;

    public record RenameRequest(String name) {
    }

    @GetMapping
    public Result<List<NamedMessageQueryService.NamedQuery>> list(@RequestParam String instanceId) {
        return Result.ok(service.list(instanceId));
    }

    @PostMapping
    public Result<Void> save(@RequestBody NamedMessageQueryService.Draft draft) {
        service.save(draft);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> rename(@PathVariable String id, @RequestParam String instanceId,
                               @RequestBody RenameRequest request) {
        service.rename(instanceId, id, request.name());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id, @RequestParam String instanceId) {
        service.delete(instanceId, id);
        return Result.ok();
    }
}
