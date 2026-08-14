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
package org.apache.rocketmq.studio.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class Result<T> {
    private int code;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorCode;
    private String message;
    private T data;

    private Result() {}

    private Result(int code, String errorCode, String message, T data) {
        this.code = code;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, null, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, null, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return error(code, null, message);
    }

    public static <T> Result<T> error(int code, String errorCode, String message) {
        return new Result<>(code, errorCode, message, null);
    }
}
