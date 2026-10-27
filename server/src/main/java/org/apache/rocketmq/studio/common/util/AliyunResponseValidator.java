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
package org.apache.rocketmq.studio.common.util;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.function.Function;

/** Shared, null-tolerant business flag validation for Aliyun read and catalog responses. */
public final class AliyunResponseValidator {
    private AliyunResponseValidator() {
    }

    /**
     * Missing response bodies are malformed upstream responses. A present body with an
     * omitted success flag remains compatible; only explicit false is a business rejection.
     * Business rejections use 422 rather than a transport/outage 502 or a Studio-session 401.
     */
    public static <T> T requireReadableBody(String operation, T body, Function<T, Boolean> success,
            Function<T, String> code, Function<T, String> message) {
        if (body == null) {
            throw new BusinessException(502, "Aliyun " + operation + " returned an empty response body");
        }
        if (Boolean.FALSE.equals(success.apply(body))) {
            String providerMessage = message.apply(body);
            String providerCode = code.apply(body);
            String detail = StringUtils.hasText(providerMessage) ? providerMessage
                    : StringUtils.hasText(providerCode) ? providerCode : "provider rejected the request";
            throw new BusinessException(422, "Aliyun " + operation + " rejected: " + detail);
        }
        return body;
    }
}
