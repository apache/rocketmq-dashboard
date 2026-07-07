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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InMemoryAuditRepository implements AuditRepository {

    private final Map<String, AuditRecordVO> records = new ConcurrentHashMap<>();

    @Override
    public List<AuditRecordVO> findAll(String search, String operationType,
                                     LocalDateTime startDate, LocalDateTime endDate,
                                     String result) {
        return records.values().stream()
                .filter(r -> search == null || search.isEmpty()
                        || r.getDetail().toLowerCase().contains(search.toLowerCase())
                        || r.getOperator().toLowerCase().contains(search.toLowerCase())
                        || r.getTarget().toLowerCase().contains(search.toLowerCase()))
                .filter(r -> operationType == null || operationType.isEmpty()
                        || operationType.equals(r.getOperationType()))
                .filter(r -> startDate == null || r.getTimestamp() != null && !r.getTimestamp().isBefore(startDate))
                .filter(r -> endDate == null || r.getTimestamp() != null && !r.getTimestamp().isAfter(endDate))
                .filter(r -> result == null || result.isEmpty() || result.equals(r.getResult()))
                .sorted((a, b) -> {
                    if (a.getTimestamp() == null || b.getTimestamp() == null) {
                        return 0;
                    }
                    return b.getTimestamp().compareTo(a.getTimestamp());
                })
                .collect(Collectors.toList());
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff) {
        List<String> toRemove = records.values().stream()
                .filter(r -> r.getTimestamp() != null && r.getTimestamp().isBefore(cutoff))
                .map(AuditRecordVO::getId)
                .collect(Collectors.toList());
        toRemove.forEach(records::remove);
        log.debug("Deleted {} audit records before {}", toRemove.size(), cutoff);
        return toRemove.size();
    }
}
