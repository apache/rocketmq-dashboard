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
package org.apache.rocketmq.studio.ops.audit;

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_RECORDS = 10_000;
    private static final String CSV_HEADER =
            "timestamp,operator,operationType,resourceType,target,clusterId,detail,result,errorMessage\r\n";

    private final AuditRepository auditRepository;


    public PageResult<AuditRecordVO> queryLogs(int page, int pageSize, String search,
                                             String operationType, String startDate,
                                             String endDate, String result) {
        validatePagination(page, pageSize);
        log.info("Querying audit logs, page={}, pageSize={}, search={}, operationType={}, result={}",
                page, pageSize, search, operationType, result);

        return findPage(search, operationType, startDate, endDate, result, page, pageSize);
    }

    public String exportLogs(String search, String operationType, String startDate,
                             String endDate, String result) {
        PageResult<AuditRecordVO> page = findPage(
                search, operationType, startDate, endDate, result, 1, MAX_EXPORT_RECORDS);
        if (page.getTotal() > MAX_EXPORT_RECORDS) {
            throw new BusinessException(400,
                    "Audit log export exceeds the maximum of " + MAX_EXPORT_RECORDS + " records; narrow the filters");
        }
        StringBuilder csv = new StringBuilder("\uFEFF").append(CSV_HEADER);
        for (AuditRecordVO record : page.getItems()) {
            appendCsvRow(csv,
                    record.getTimestamp(),
                    record.getOperator(),
                    record.getOperationType(),
                    record.getResourceType(),
                    record.getTarget(),
                    record.getClusterId(),
                    record.getDetail(),
                    record.getResult(),
                    record.getErrorMessage());
        }
        return csv.toString();
    }


    public void record(String operationType, String target, String detail, String result) {
        record(operationType, target, null, detail, result);
    }

    public void record(String operationType, String target, String clusterId, String detail, String result) {
        AuditRecordVO record = AuditRecordVO.builder()
                .timestamp(LocalDateTime.now())
                .operator(AuthenticatedUserContext.currentUsernameOrSystem())
                .operationType(operationType)
                .target(target)
                .clusterId(clusterId)
                .detail(detail)
                .result(result)
                .build();
        auditRepository.save(record);
        log.info("Audit recorded: {} on {} -> {}", operationType, target, result);
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
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private PageResult<AuditRecordVO> findPage(String search, String operationType, String startDate,
                                               String endDate, String result, int page, int pageSize) {
        LocalDateTime start = parseDate(startDate, true, "startDate");
        LocalDateTime end = parseDate(endDate, false, "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(400, "startDate must not be after endDate");
        }
        return auditRepository.findPage(search, operationType, start, end, result, page, pageSize);
    }

    private void appendCsvRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(toCsvCell(values[i]));
        }
        csv.append("\r\n");
    }

    private String toCsvCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private LocalDateTime parseDate(String dateStr, boolean startOfDay, String parameterName) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return startOfDay ? date.atStartOfDay() : date.atTime(LocalTime.MAX);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, parameterName + " must use YYYY-MM-DD");
        }
    }
}
