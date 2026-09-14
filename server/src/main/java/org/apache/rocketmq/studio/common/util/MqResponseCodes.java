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

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;

/**
 * Shared classifier for the RocketMQ response codes carried on broker/client exceptions.
 * Consolidates the per-provider copies that walk an exception cause chain looking for a
 * specific {@code ResponseCode} so the "RPC succeeded but there is no business data" grading
 * stays consistent. {@link MQClientException} and {@link MQBrokerException} both expose
 * {@code getResponseCode()} but share no common supertype, so both are checked.
 */
public final class MqResponseCodes {

    private MqResponseCodes() {
    }

    /**
     * Returns {@code true} when any exception in the cause chain is an {@link MQClientException}
     * or {@link MQBrokerException} carrying one of the given response codes. A self-referential
     * cause terminates the walk instead of looping forever.
     */
    public static boolean hasResponseCode(Throwable error, int... responseCodes) {
        Throwable cause = error;
        while (cause != null) {
            Integer code = responseCodeOf(cause);
            if (code != null) {
                for (int responseCode : responseCodes) {
                    if (code == responseCode) {
                        return true;
                    }
                }
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    private static Integer responseCodeOf(Throwable throwable) {
        if (throwable instanceof MQClientException clientException) {
            return clientException.getResponseCode();
        }
        if (throwable instanceof MQBrokerException brokerException) {
            return brokerException.getResponseCode();
        }
        return null;
    }
}
