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
package org.apache.rocketmq.studio.ops.ai.tool.core;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ToolExecutionException extends BusinessException {

    private final String errorCode;
    private final String hint;

    public ToolExecutionException(
            HttpStatus httpStatus,
            String errorCode,
            String message,
            String hint) {
        super(httpStatus, message);
        this.errorCode = errorCode;
        this.hint = hint;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getHint() {
        return hint;
    }

    public static ToolExecutionException from(BusinessException exception) {
        if (exception instanceof ToolExecutionException toolException) {
            return toolException;
        }
        HttpStatus status = HttpStatus.resolve(exception.getCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ToolExecutionException translated = new ToolExecutionException(
                status,
                ToolError.EXECUTION_FAILED.code(),
                exception.getMessage(),
                ToolError.EXECUTION_FAILED.hint());
        translated.initCause(exception);
        return translated;
    }

}
