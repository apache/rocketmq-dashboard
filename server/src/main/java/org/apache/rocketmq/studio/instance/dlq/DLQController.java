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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
@Validated
public class DLQController {

    private static final String HEADER_EXPORT_TRUNCATED = "X-DLQ-Export-Truncated";
    private static final String HEADER_EXPORT_FAILED_QUEUES = "X-DLQ-Export-FailedQueues";
    private static final String HEADER_EXPORT_LIMIT = "X-DLQ-Export-Limit";
    private static final String EXCEL_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_SELECTED_MESSAGES = 100;

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

    @PostMapping("/resend-selected")
    public Result<DLQResendResultVO> resendSelectedMessages(
            @Valid @RequestBody(required = false) DLQResendSelectedRequestDTO request) {
        requireSelectedRequest(request);
        return Result.ok(dlqService.resendSelectedMessages(request.getInstanceId(), request.getGroupName(),
                request.getMsgIds(), request.getTargetTopic()));
    }

    @GetMapping("/{groupName}/messages")
    public Result<PageResult<DLQMessageVO>> listDLQMessages(@PathVariable String groupName,
            @RequestParam String instanceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @Min(value = 1, message = "page must be at least 1")
            @RequestParam(defaultValue = "1") int page,
            @Min(value = 1, message = "pageSize must be at least 1")
            @Max(value = 100, message = "pageSize must not exceed 100")
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(dlqService.listMessages(instanceId, groupName, startTime, endTime, page, pageSize));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportDLQExcel(@RequestParam String instanceId,
                                                 @RequestParam String groupName,
                                                 @RequestParam(required = false) Long startTime,
                                                 @RequestParam(required = false) Long endTime,
                                                 @Size(max = MAX_SELECTED_MESSAGES,
                                                         message = "At most 100 msgIds are allowed per export")
                                                 @RequestParam(required = false) List<String> msgIds) {
        DLQExcelExportResultVO result = dlqService.exportExcel(instanceId, groupName, startTime, endTime, msgIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        attachmentDisposition("dlq-" + sanitizeForFilename(groupName) + ".xlsx").toString())
                .header(HEADER_EXPORT_TRUNCATED, String.valueOf(result.isTruncated()))
                .header(HEADER_EXPORT_FAILED_QUEUES, String.valueOf(result.getFailedQueueCount()))
                .header(HEADER_EXPORT_LIMIT, String.valueOf(result.getLimit()))
                .contentType(MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
                .body(result.getData());
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
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        attachmentDisposition("dlq-" + sanitizeForFilename(groupName) + ".json").toString())
                .header(HEADER_EXPORT_TRUNCATED, String.valueOf(result.isTruncated()))
                .header(HEADER_EXPORT_FAILED_QUEUES, String.valueOf(result.getFailedQueueCount()))
                .header(HEADER_EXPORT_LIMIT, String.valueOf(result.getLimit()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Builds an {@code attachment} Content-Disposition; non-ASCII names need the RFC 5987
     * {@code filename*} parameter so browsers keep the original characters instead of the
     * lossy ASCII fallback.
     */
    private static ContentDisposition attachmentDisposition(String fileName) {
        ContentDisposition.Builder builder = ContentDisposition.attachment();
        if (fileName.chars().allMatch(ch -> ch < 128)) {
            builder.filename(fileName);
        } else {
            builder.filename(fileName, StandardCharsets.UTF_8);
        }
        return builder.build();
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

    private void requireSelectedRequest(DLQResendSelectedRequestDTO request) {
        if (request == null) {
            throw new BusinessException(400, "DLQ resend request is required");
        }
    }
}
