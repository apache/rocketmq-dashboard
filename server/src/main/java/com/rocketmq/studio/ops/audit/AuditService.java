/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rocketmq.studio.ops.audit;

import com.rocketmq.studio.common.domain.PageResult;
import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;


    public PageResult<AuditRecordVO> queryLogs(int page, int pageSize, String search,
                                             String operationType, String startDate,
                                             String endDate, String result) {
        validatePagination(page, pageSize);
        log.info("Querying audit logs, page={}, pageSize={}, search={}, operationType={}, result={}",
                page, pageSize, search, operationType, result);

        LocalDateTime start = parseDate(startDate, true);
        LocalDateTime end = parseDate(endDate, false);

        List<AuditRecordVO> allRecords = auditRepository.findAll(search, operationType, start, end, result);
        long total = allRecords.size();

        long offset = (long) (page - 1) * pageSize;
        int fromIndex = (int) Math.min(offset, allRecords.size());
        int toIndex = (int) Math.min((long) fromIndex + pageSize, allRecords.size());
        List<AuditRecordVO> pageRecords = allRecords.subList(fromIndex, toIndex);

        return PageResult.of(pageRecords, total, page, pageSize);
    }


    public int cleanupLogs(int beforeDays) {
        if (beforeDays <= 0) {
            throw new BusinessException(400, "beforeDays must be greater than 0");
        }
        log.info("Cleaning up audit logs older than {} days", beforeDays);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(beforeDays);
        return auditRepository.deleteBefore(cutoff);
    }

    private void validatePagination(int page, int pageSize) {
        if (page <= 0) {
            throw new BusinessException(400, "page must be greater than 0");
        }
        if (pageSize <= 0) {
            throw new BusinessException(400, "pageSize must be greater than 0");
        }
    }

    private LocalDateTime parseDate(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return startOfDay ? date.atStartOfDay() : date.atTime(LocalTime.MAX);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr, e);
            return null;
        }
    }
}
