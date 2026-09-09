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

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Helpers for reading broker runtime-stats ({@code KVTable}) entries whose keys differ across
 * broker generations. Consolidates the per-provider copies so the legacy-key fallback stays
 * consistent everywhere runtime stats are parsed.
 */
public final class BrokerRuntimeStats {

    /** 5.x brokers publish outbound TPS under this key (double r). */
    private static final String OUTBOUND_TPS_5X = "getTransferredTps";
    /** 4.x brokers use the historical single-r spelling. */
    private static final String OUTBOUND_TPS_4X = "getTransferedTps";

    private BrokerRuntimeStats() {
    }

    /**
     * Resolves the outbound TPS entry: 5.x brokers emit {@code getTransferredTps} while 4.x brokers
     * emit the historical {@code getTransferedTps} spelling. A present-but-blank 5.x value falls
     * back to the 4.x key rather than being treated as the answer. Returns null when neither key
     * carries a usable value.
     */
    public static String outboundTps(Map<String, String> runtimeStats) {
        if (runtimeStats == null) {
            return null;
        }
        String transferred = runtimeStats.get(OUTBOUND_TPS_5X);
        if (StringUtils.hasText(transferred)) {
            return transferred;
        }
        return runtimeStats.get(OUTBOUND_TPS_4X);
    }

    /**
     * Computes a daily counter from the morning-snapshot pair a broker publishes in its runtime
     * stats (e.g. {@code msgPutTotalTodayMorning}/{@code msgPutTotalTodayNow}): the delta is the
     * current value minus the morning snapshot, clamped at zero so a broker restart (which resets
     * the counters) yields 0 instead of a negative number. Returns 0 when either key is missing,
     * negative, or unparseable.
     */
    public static long dailyCounterDelta(Map<String, String> runtimeStats, String morningKey, String nowKey) {
        String morningValue = runtimeStats == null ? null : runtimeStats.get(morningKey);
        String nowValue = runtimeStats == null ? null : runtimeStats.get(nowKey);
        if (morningValue == null || nowValue == null) {
            return 0L;
        }
        try {
            long morning = Long.parseLong(morningValue.trim());
            long now = Long.parseLong(nowValue.trim());
            if (morning < 0 || now < 0) {
                return 0L;
            }
            return Math.max(0L, now - morning);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
