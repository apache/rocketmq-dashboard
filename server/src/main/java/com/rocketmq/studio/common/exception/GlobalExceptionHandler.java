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
package com.rocketmq.studio.common.exception;

import com.rocketmq.studio.auth.security.StudioLoginException;
import com.rocketmq.studio.cluster.metrics.PrometheusException;
import com.rocketmq.studio.common.domain.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String INVALID_LOGIN_REQUEST = "Invalid login request";
    private static final String INVALID_LOGIN_CREDENTIALS = "Invalid username or password";
    private static final String TOO_MANY_LOGIN_ATTEMPTS = "Too many login attempts";
    private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            log.warn("Invalid Studio login request");
            return loginError(HttpStatus.BAD_REQUEST, INVALID_LOGIN_REQUEST);
        }
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity.status(ex.getCode())
            .body(Result.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(PrometheusException.class)
    public ResponseEntity<Result<?>> handlePrometheusException(
        PrometheusException ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            log.error("Unexpected exception during Studio login");
            return loginError(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
        log.warn("Prometheus exception: status={}, message={}", ex.getStatusCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Result.error(ex.getStatusCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            log.warn("Invalid Studio login request");
            return loginError(HttpStatus.BAD_REQUEST, INVALID_LOGIN_REQUEST);
        }
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest()
            .body(Result.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        log.warn("Invalid request body");
        if (isLoginRequest(request)) {
            return loginError(HttpStatus.BAD_REQUEST, INVALID_LOGIN_REQUEST);
        }
        return ResponseEntity.badRequest()
            .body(Result.error(HttpStatus.BAD_REQUEST.value(), "Invalid request body"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleHttpMediaTypeNotAcceptableException(
        HttpMediaTypeNotAcceptableException ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            return loginEmpty(HttpStatus.NOT_ACCEPTABLE);
        }
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleHttpMediaTypeNotSupportedException(
        HttpMediaTypeNotSupportedException ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            return loginEmpty(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleHttpRequestMethodNotSupportedException(
        HttpRequestMethodNotSupportedException ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .cacheControl(CacheControl.noStore())
                .allow(HttpMethod.POST)
                .build();
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(
        NoResourceFoundException ex,
        HttpServletRequest request
    ) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri != null
            && contextPath != null
            && requestUri.equals(contextPath + LOGIN_PATH + "/")) {
            return loginEmpty(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(StudioLoginException.class)
    public ResponseEntity<Result<?>> handleStudioLoginException(StudioLoginException ex) {
        if (ex.status() == HttpStatus.UNAUTHORIZED) {
            return loginError(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_CREDENTIALS);
        }
        if (ex.status() == HttpStatus.TOO_MANY_REQUESTS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .cacheControl(CacheControl.noStore())
                .header(
                HttpHeaders.RETRY_AFTER,
                Long.toString(Math.max(1, ex.retryAfterSeconds()))
                )
                .body(Result.error(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    TOO_MANY_LOGIN_ATTEMPTS
                ));
        }
        log.error("Unexpected Studio login exception status");
        return loginError(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(
        Exception ex,
        HttpServletRequest request
    ) {
        if (isLoginRequest(request)) {
            log.error("Unexpected exception during Studio login");
            return loginError(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
        log.error("Unexpected exception", ex);
        return ResponseEntity.internalServerError()
            .body(Result.error(500, INTERNAL_SERVER_ERROR));
    }

    private static boolean isLoginRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return requestUri != null
            && contextPath != null
            && requestUri.equals(contextPath + LOGIN_PATH);
    }

    private static ResponseEntity<Result<?>> loginError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(Result.error(status.value(), message));
    }

    private static ResponseEntity<Void> loginEmpty(HttpStatus status) {
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .build();
    }
}
