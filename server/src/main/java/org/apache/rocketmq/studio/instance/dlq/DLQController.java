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
package org.apache.rocketmq.studio.instance.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DLQController {

    private static final String HEADER_EXPORT_TRUNCATED = "X-DLQ-Export-Truncated";
    private static final String HEADER_EXPORT_FAILED_QUEUES = "X-DLQ-Export-FailedQueues";
    private static final String HEADER_EXPORT_LIMIT = "X-DLQ-Export-Limit";

    private final DLQService dlqService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<PageResult<DLQGroupVO>> listDLQGroups(@RequestParam String instanceId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(dlqService.listDLQGroups(instanceId, search, page, pageSize));
    }

    @PostMapping("/resend")
    public Result<DLQResendResultVO> resendMessages(@Valid @RequestBody(required = false) DLQResendRequestDTO request) {
        requireRequest(request);
        return Result.ok(dlqService.resendMessages(request.getInstanceId(), request.getGroupName(),
                request.getStartTime(), request.getEndTime(), request.getTargetTopic()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDLQMessages(@RequestParam String instanceId,
                                                    @RequestParam String groupName,
                                                    @RequestParam(required = false) Long startTime,
                                                    @RequestParam(required = false) Long endTime,
                                                    @RequestParam(required = false) Integer maxCount) {
        DLQExportResultVO result = dlqService.exportMessages(
                instanceId, groupName, startTime, endTime, maxCount);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(result.getMessages());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize DLQ export");
        }
        String fileName = "dlq-" + sanitizeForFilename(groupName) + ".json";
        ContentDisposition.Builder builder = ContentDisposition.attachment();
        if (fileName.chars().allMatch(ch -> ch < 128)) {
            builder.filename(fileName);
        } else {
            // Non-ASCII names need the RFC 5987 filename* parameter so browsers keep the
            // original characters instead of the lossy ASCII fallback.
            builder.filename(fileName, StandardCharsets.UTF_8);
        }
        ContentDisposition disposition = builder.build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HEADER_EXPORT_TRUNCATED, String.valueOf(result.isTruncated()))
                .header(HEADER_EXPORT_FAILED_QUEUES, String.valueOf(result.getFailedQueueCount()))
                .header(HEADER_EXPORT_LIMIT, String.valueOf(result.getLimit()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Strips the characters that would break a quoted RFC 6266 header value or confuse
     * file managers; the non-ASCII part is preserved and emitted as an RFC 5987
     * {@code filename*} parameter by Spring.
     */
    private static String sanitizeForFilename(String raw) {
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            cleaned.append(c == '"' || c == '\\' || c < 0x20 || c == 0x7f ? '_' : c);
        }
        return cleaned.toString();
    }

    private void requireRequest(DLQResendRequestDTO request) {
        if (request == null) {
            throw new BusinessException(400, "DLQ resend request is required");
        }
    }
}
