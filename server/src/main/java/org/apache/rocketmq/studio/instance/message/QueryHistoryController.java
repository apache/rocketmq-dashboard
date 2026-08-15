/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/query-history")
@RequiredArgsConstructor
public class QueryHistoryController {
    private static final int MAX_PAGE_SIZE = 100;
    private final QueryHistoryService queryHistoryService;

    @GetMapping("/messages")
    public Result<PageResult<MessageQueryHistoryVO>> listMessageQueries(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String queryType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        return Result.ok(queryHistoryService.listMessageQueries(
                normalizeFilter(clusterId), normalizeFilter(queryType), normalizeFilter(search),
                page, pageSize));
    }

    @GetMapping("/traces")
    public Result<PageResult<TraceQueryHistoryVO>> listTraceQueries(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        return Result.ok(queryHistoryService.listTraceQueries(
                normalizeFilter(clusterId), normalizeFilter(search), page, pageSize));
    }

    @GetMapping("/summary")
    public Result<QueryHistorySummaryVO> summary(@RequestParam(required = false) String clusterId) {
        return Result.ok(queryHistoryService.summarize(normalizeFilter(clusterId)));
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be at least 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
