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
package org.apache.rocketmq.studio.ops.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ToolController.class, McpToolController.class})
public class ToolExceptionHandler {

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<Map<String, String>> handleToolExecutionException(
            ToolExecutionException exception) {
        if (exception.getCode() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Tool execution failed: code={}, message={}",
                    exception.getErrorCode(), exception.getMessage(), exception);
        } else {
            log.warn("Tool execution failed: code={}, message={}",
                    exception.getErrorCode(), exception.getMessage());
        }
        return error(exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody() {
        return error(ToolError.REQUEST_BODY_INVALID.exception());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParameter(
            MissingServletRequestParameterException exception) {
        return error(ToolError.REQUEST_PARAMETER_REQUIRED.exception(exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleInvalidParameter(
            MethodArgumentTypeMismatchException exception) {
        return error(ToolError.REQUEST_PARAMETER_INVALID.exception(exception.getName()));
    }

    private static ResponseEntity<Map<String, String>> error(ToolExecutionException exception) {
        return ResponseEntity.status(exception.getCode()).body(Map.of(
                "code", exception.getErrorCode(),
                "message", exception.getMessage(),
                "hint", exception.getHint()));
    }
}
