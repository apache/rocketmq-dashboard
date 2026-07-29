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

public class Result<T> {
    private int status;
    private T data;
    private String errMsg;

    private Result() {}

    private Result(int status, String errMsg, T data) {
        this.status = status;
        this.errMsg = errMsg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, null, data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, null, null);
    }

    public static <T> Result<T> error(int status, String errMsg) {
        return new Result<>(status, errMsg, null);
    }

    public int getStatus() { return status; }
    public String getErrMsg() { return errMsg; }
    public T getData() { return data; }
}
