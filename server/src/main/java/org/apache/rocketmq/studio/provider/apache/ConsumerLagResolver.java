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

/**
 * Resolves the consumer lag (diff between broker offset and consumer offset) without masking the
 * {@code -1} "unknown" sentinel that RocketMQ 5.0 gRPC consumers report.
 *
 * <p>The legacy dashboard clamped every negative diff to {@code 0} with {@code Math.max(0, ...)},
 * which hid the unknown state and produced a misleading {@code NOT_CONSUME_YET} / zero-lag view.
 * This resolver keeps the raw diff when it is valid, and falls back to the proxy transport when the
 * broker reports {@code -1}. When no proxy is available it returns {@link #UNKNOWN} so the UI can
 * show the genuine unknown state instead of a fabricated zero.
 */
public final class ConsumerLagResolver {

    /** Sentinel meaning "lag cannot be determined" (matches the broker's own -1 for gRPC). */
    public static final long UNKNOWN = -1;

    private ConsumerLagResolver() {
    }

    /**
     * @param brokerDiff raw brokerOffset - consumerOffset (may be negative for 5.0 gRPC consumers)
     * @param proxy      optional proxy stats source; may be {@code null}
     * @return the resolved lag, or {@link #UNKNOWN} when it cannot be determined
     */
    public static long resolve(long brokerDiff, ProxyStatsProvider proxy) {
        if (brokerDiff >= 0) {
            return brokerDiff;
        }
        if (proxy != null) {
            return proxy.queryLag();
        }
        return UNKNOWN;
    }
}
