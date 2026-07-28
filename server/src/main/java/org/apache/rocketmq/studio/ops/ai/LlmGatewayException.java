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

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LlmGatewayException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final String hint;

    public LlmGatewayException(String message) {
        this(HttpStatus.BAD_GATEWAY.value(), "llm.gateway_error", message, null, null);
    }

    public LlmGatewayException(String message, Throwable cause) {
        this(HttpStatus.BAD_GATEWAY.value(), "llm.gateway_error", message, null, cause);
    }

    public LlmGatewayException(int statusCode, String code, String message, String hint) {
        this(statusCode, code, message, hint, null);
    }

    public LlmGatewayException(int statusCode, String code, String message, String hint, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
        this.hint = hint;
    }
}
