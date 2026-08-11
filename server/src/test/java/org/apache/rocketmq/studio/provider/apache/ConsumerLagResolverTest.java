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
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerLagResolverTest {

    @Test
    void positiveBrokerDiffIsReturnedAsIs() {
        assertThat(ConsumerLagResolver.resolve(100, null)).isEqualTo(100);
        assertThat(ConsumerLagResolver.resolve(0, null)).isEqualTo(0);
    }

    @Test
    void negativeBrokerDiffWithoutProxyReturnsUnknown() {
        assertThat(ConsumerLagResolver.resolve(-1, null))
                .isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void negativeBrokerDiffWithProxyReturnsProxyValue() {
        ProxyStatsProvider proxy = Mockito.mock(ProxyStatsProvider.class);
        Mockito.when(proxy.queryLag()).thenReturn(42L);
        assertThat(ConsumerLagResolver.resolve(-1, proxy)).isEqualTo(42);
    }

    @Test
    void negativeBrokerDiffWithNoopProxyReturnsUnknown() {
        ProxyStatsProvider proxy = new NoopProxyStatsProvider();
        assertThat(ConsumerLagResolver.resolve(-1, proxy))
                .isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void positiveBrokerDiffIgnoresProxy() {
        ProxyStatsProvider proxy = Mockito.mock(ProxyStatsProvider.class);
        Mockito.when(proxy.queryLag()).thenReturn(999L);
        assertThat(ConsumerLagResolver.resolve(7, proxy)).isEqualTo(7);
    }
}
