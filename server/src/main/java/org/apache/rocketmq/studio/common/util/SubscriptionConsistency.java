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

/**
 * Maps a vendor subscription consistency flag to the studio {@code consistency} value.
 * The Apache provider reports {@code consistent} when a client of the group is connected and
 * leaves the status unknown (null) otherwise, and that is the only vocabulary the console
 * understands: the subscription table colours the cell and the "inconsistent only" filter
 * match {@code consistent} / {@code inconsistent}. The cloud providers therefore have to
 * translate their own flags instead of leaking {@code true} / {@code false} or {@code 0} /
 * {@code 1} into the API response.
 */
public final class SubscriptionConsistency {

    /** Every client of the consumer group subscribes the same way. */
    public static final String CONSISTENT = "consistent";

    /** At least one client of the consumer group subscribes differently from the others. */
    public static final String INCONSISTENT = "inconsistent";

    private SubscriptionConsistency() {
    }

    /**
     * Aliyun {@code ListConsumerGroupSubscriptions} returns the consistency as a boolean, where
     * {@code true} means the subscription relationship is consistent. A missing value stays
     * unknown instead of being reported as inconsistent.
     */
    public static String fromBoolean(Boolean consistency) {
        if (consistency == null) {
            return null;
        }
        return consistency ? CONSISTENT : INCONSISTENT;
    }

    /**
     * Tencent {@code DescribeTopicListByGroup} returns the consistency as a code, where {@code 0}
     * means consistent and {@code 1} means inconsistent. A missing or undocumented code stays
     * unknown instead of being guessed.
     */
    public static String fromCode(Long consistency) {
        if (consistency == null) {
            return null;
        }
        if (consistency.longValue() == 0L) {
            return CONSISTENT;
        }
        if (consistency.longValue() == 1L) {
            return INCONSISTENT;
        }
        return null;
    }
}
