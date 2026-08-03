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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String CSV_HEADER =
            "timestamp,operator,operationType,target,detail,ipAddress,result\r\n";

    private final AuditRepository auditRepository;


    public PageResult<AuditRecordVO> queryLogs(int page, int pageSize, String search,
                                             String operationType, String startDate,
                                             String endDate, String result) {
        validatePagination(page, pageSize);
        log.info("Querying audit logs, page={}, pageSize={}, search={}, operationType={}, result={}",
                page, pageSize, search, operationType, result);

        List<AuditRecordVO> allRecords = findRecords(search, operationType, startDate, endDate, result);
        long total = allRecords.size();

        long offset = (long) (page - 1) * pageSize;
        int fromIndex = (int) Math.min(offset, allRecords.size());
        int toIndex = (int) Math.min((long) fromIndex + pageSize, allRecords.size());
        List<AuditRecordVO> pageRecords = allRecords.subList(fromIndex, toIndex);

        return PageResult.of(pageRecords, total, page, pageSize);
    }

    public String exportLogs(String search, String operationType, String startDate,
                             String endDate, String result) {
        List<AuditRecordVO> records = findRecords(search, operationType, startDate, endDate, result);
        StringBuilder csv = new StringBuilder("\uFEFF").append(CSV_HEADER);
        for (AuditRecordVO record : records) {
            appendCsvRow(csv,
                    record.getTimestamp(),
                    record.getOperator(),
                    record.getOperationType(),
                    record.getTarget(),
                    record.getDetail(),
                    record.getIpAddress(),
                    record.getResult());
        }
        return csv.toString();
    }


    public void record(String operationType, String target, String detail, String result) {
        AuditRecordVO record = AuditRecordVO.builder()
                .timestamp(LocalDateTime.now())
                .operationType(operationType)
                .target(target)
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
        if (pageSize <= 0) {
            throw new BusinessException(400, "pageSize must be greater than 0");
        }
    }

    private List<AuditRecordVO> findRecords(String search, String operationType, String startDate,
                                            String endDate, String result) {
        LocalDateTime start = parseDate(startDate, true, "startDate");
        LocalDateTime end = parseDate(endDate, false, "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(400, "startDate must not be after endDate");
        }
        return auditRepository.findAll(search, operationType, start, end, result);
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
