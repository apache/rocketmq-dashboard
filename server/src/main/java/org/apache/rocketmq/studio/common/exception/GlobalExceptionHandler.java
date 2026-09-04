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
package org.apache.rocketmq.studio.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.apache.rocketmq.studio.cluster.metrics.PrometheusException;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity.status(ex.getCode())
                .body(Result.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public Result<?> handleUnsupportedOperationException(UnsupportedOperationException ex) {
        log.warn("Unsupported operation: {}", ex.getMessage());
        return Result.error(HttpStatus.NOT_IMPLEMENTED.value(), ex.getMessage());
    }

    @ExceptionHandler(PrometheusException.class)
    public ResponseEntity<Result<?>> handlePrometheusException(PrometheusException ex) {
        log.warn("Prometheus exception: status={}, message={}", ex.getStatusCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Result.error(ex.getStatusCode(), ex.getMessage()));
    }

    @ExceptionHandler(LlmGatewayException.class)
    public ResponseEntity<Result<?>> handleLlmGatewayException(LlmGatewayException ex) {
        log.warn("LLM gateway exception: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Result.error(ex.getStatusCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                .orElse("Invalid request");
        return Result.error(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * Constraint violations on {@code @Validated} request parameters (e.g. {@code @Size}
     * on a {@code @RequestParam}) surface as {@link ConstraintViolationException} rather
     * than {@link MethodArgumentNotValidException}; report them as 400 as well.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage() == null ? "Invalid request" : violation.getMessage())
                .orElse("Invalid request");
        return Result.error(HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Invalid request body");
        return Result.error(HttpStatus.BAD_REQUEST.value(), "Invalid request body");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBadRequestParameterException(Exception ex) {
        log.warn("Invalid request parameter: {}", ex.getMessage());
        return Result.error(HttpStatus.BAD_REQUEST.value(), "Invalid request parameter");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception ex) {
        log.error("Unexpected exception", ex);
        return Result.error(500, "Internal Server Error");
    }

    /**
     * Spring MVC throws {@link NoResourceFoundException} for unmatched routes. Without an
     * explicit handler it would fall through to the generic catch-all and be reported as
     * HTTP 500 instead of the correct 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return Result.error(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    /**
     * Requests with an unsupported HTTP method must be reported as 405, not 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMessage());
        return Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage());
    }

    /**
     * Requests with an unsupported content type must be reported as 415, not 500.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<?> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        return Result.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), ex.getMessage());
    }

    /**
     * Requests that cannot accept any available response representation must be
     * reported as 406, not 500.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public Result<?> handleHttpMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException ex) {
        log.warn("No acceptable response media type: {}", ex.getMessage());
        return Result.error(HttpStatus.NOT_ACCEPTABLE.value(), ex.getMessage());
    }
}
