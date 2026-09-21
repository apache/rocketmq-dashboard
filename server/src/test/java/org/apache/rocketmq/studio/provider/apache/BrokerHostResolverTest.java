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

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerHostResolverTest {

    @Test
    void defaultResolverResolvesANumericLiteralWithoutDnsTest() throws Exception {
        // The decoded side of an offset msgId is always a numeric literal, so the default resolver
        // answers it from the parse path and never reaches the network in the common case.
        assertThat(BrokerHostResolver.DEFAULT.resolve("172.30.10.100"))
                .isEqualTo(InetAddress.getByName("172.30.10.100"));
    }

    @Test
    void cachingResolverLooksUpEachDistinctHostOnceTest() throws Exception {
        FakeBrokerHostResolver delegate = new FakeBrokerHostResolver();
        delegate.registered("broker-a.example.com", "172.30.10.100");
        delegate.registered("broker-b.example.com", "172.30.10.101");
        BrokerHostResolver caching = BrokerHostResolver.caching(delegate);

        assertThat(caching.resolve("broker-a.example.com")).isEqualTo(InetAddress.getByName("172.30.10.100"));
        assertThat(caching.resolve("broker-b.example.com")).isEqualTo(InetAddress.getByName("172.30.10.101"));
        assertThat(caching.resolve("broker-a.example.com")).isEqualTo(InetAddress.getByName("172.30.10.100"));

        assertThat(delegate.lookups())
                .containsExactly("broker-a.example.com", "broker-b.example.com");
    }

    @Test
    void cachingResolverRemembersAFailedLookupTest() {
        FakeBrokerHostResolver delegate = new FakeBrokerHostResolver();
        BrokerHostResolver caching = BrokerHostResolver.caching(delegate);

        assertThatThrownBy(() -> caching.resolve("broker-0.invalid"))
                .isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> caching.resolve("broker-0.invalid"))
                .isInstanceOf(UnknownHostException.class);

        assertThat(delegate.lookups()).containsExactly("broker-0.invalid");
    }
}
