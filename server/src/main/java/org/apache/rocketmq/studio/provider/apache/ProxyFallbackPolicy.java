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

package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;

import java.util.regex.Pattern;

/**
 * Pure policy that decides whether a failed broker request should be retried through a RocketMQ
 * 5.0 proxy.
 *
 * <p>Connecting the dashboard directly to a 5.0 broker cluster (or a proxy acting on its behalf)
 * raises {@link MQBrokerException} with a description such as {@code "request type 106 not
 * supported"} or {@code "request type 206 not supported"} for request codes the proxy does not
 * forward. This policy detects that condition and the proxy access mode so callers can fall back
 * to a proxy transport instead of crashing.
 *
 * <p>This class is intentionally free of transport logic: the real proxy request execution is a
 * separate deliverable. Wiring sites consult {@link #shouldFallback(MQBrokerException, ClusterType)}
 * to decide whether to retry.
 */
public final class ProxyFallbackPolicy {

    private static final Pattern UNSUPPORTED_REQUEST_CODE =
            Pattern.compile("request type \\d+ not supported", Pattern.CASE_INSENSITIVE);

    private ProxyFallbackPolicy() {
    }

    /**
     * @return {@code true} when the broker exception describes an unsupported request code
     *         (e.g. {@code 106} / {@code 206} not supported).
     */
    public static boolean isUnsupportedRequestCode(MQBrokerException ex) {
        if (ex == null) {
            return false;
        }
        String desc = ex.getErrorMessage() != null ? ex.getErrorMessage() : ex.getMessage();
        return desc != null && UNSUPPORTED_REQUEST_CODE.matcher(desc).find();
    }

    /** @return {@code true} for RocketMQ 5.0 proxy local or cluster access modes. */
    public static boolean isProxyMode(ClusterType type) {
        return type == ClusterType.V5_PROXY_LOCAL || type == ClusterType.V5_PROXY_CLUSTER;
    }

    /**
     * Combines {@link #isUnsupportedRequestCode(MQBrokerException)} and {@link #isProxyMode(ClusterType)}.
     */
    public static boolean shouldFallback(MQBrokerException ex, ClusterType type) {
        return isUnsupportedRequestCode(ex) && isProxyMode(type);
    }
}
