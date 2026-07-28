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
package org.apache.rocketmq.dashboard.exception;

/**
 * Custom exception for service layer operations.
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 9213584003139969215L;
    private int code;

    /**
     * Constructs a ServiceException with code and message.
     * @param code the error code
     * @param message the error message
     */
    public ServiceException(final int code, final String message) {
        super(message);
        this.code = code;
    }

    /**
     * Constructs a ServiceException with code, message and root cause,
     * preserving the original exception for diagnosis.
     * @param code the error code
     * @param message the error message
     * @param cause the root cause
     */
    public ServiceException(final int code, final String message, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Gets the error code.
     * @return the error code
     */
    public int getCode() {
        return code;
    }
}
