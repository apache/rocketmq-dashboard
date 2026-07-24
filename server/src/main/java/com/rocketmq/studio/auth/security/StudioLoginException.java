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
package com.rocketmq.studio.auth.security;

import org.springframework.http.HttpStatus;

public final class StudioLoginException extends RuntimeException {
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String RATE_LIMITED = "Too many login attempts";

    private final HttpStatus status;
    private final long retryAfterSeconds;

    private StudioLoginException(HttpStatus status, String message, long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static StudioLoginException invalidCredentials() {
        return new StudioLoginException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, 0);
    }

    public static StudioLoginException rateLimited(long retryAfterSeconds) {
        return new StudioLoginException(
            HttpStatus.TOO_MANY_REQUESTS,
            RATE_LIMITED,
            Math.max(1, retryAfterSeconds)
        );
    }

    public HttpStatus status() {
        return status;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
