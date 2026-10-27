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

package org.apache.rocketmq.studio.instance.acl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Outcome of pushing the dashboard's stored ACL accounts and policies to the brokers of an
 * Apache instance. The push is additive: accounts the dashboard does not know about are left
 * untouched on the broker, and every account/broker pair reports its own result so a single
 * unreachable broker cannot hide the successes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushAclToBrokerResultVO {
    private String instanceId;
    /** Master broker addresses the push targeted. */
    private List<String> brokers;
    /** Accounts written to at least one broker without error. */
    private int pushed;
    /** Accounts that failed on every broker they were pushed to. */
    private int failed;
    private List<AccountOutcome> accounts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountOutcome {
        private String username;
        /** Broker ACL 2.0 subject the account was written under, e.g. {@code User:alice}. */
        private String subject;
        /** Policies written for the subject. */
        private int policyCount;
        /** Stored rules the broker ACL model cannot express, with the reason. */
        private List<String> skipped;
        private List<BrokerOutcome> brokers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerOutcome {
        private String broker;
        private boolean success;
        private String error;
    }
}
