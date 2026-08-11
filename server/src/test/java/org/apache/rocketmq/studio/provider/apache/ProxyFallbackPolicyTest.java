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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyFallbackPolicyTest {

    @Test
    void detectsRequestCode106NotSupported() {
        MQBrokerException ex = new MQBrokerException(206, "request type 106 not supported");
        assertThat(ProxyFallbackPolicy.isUnsupportedRequestCode(ex)).isTrue();
    }

    @Test
    void detectsRequestCode206NotSupported() {
        MQBrokerException ex = new MQBrokerException(206, "request type 206 not supported");
        assertThat(ProxyFallbackPolicy.isUnsupportedRequestCode(ex)).isTrue();
    }

    @Test
    void detectsGenericRequestCodeNotSupported() {
        MQBrokerException ex = new MQBrokerException(11, "request type 333 not supported");
        assertThat(ProxyFallbackPolicy.isUnsupportedRequestCode(ex)).isTrue();
    }

    @Test
    void ignoresUnrelatedBrokerErrors() {
        MQBrokerException ex = new MQBrokerException(1, "topic already exists");
        assertThat(ProxyFallbackPolicy.isUnsupportedRequestCode(ex)).isFalse();
    }

    @Test
    void detectsFromGenericMessageWhenErrorMessageMissing() {
        MQBrokerException ex = new MQBrokerException(206, (String) null) {
            @Override
            public String getMessage() {
                return "CODE: 206  DESC: request type 206 not supported";
            }
        };
        assertThat(ProxyFallbackPolicy.isUnsupportedRequestCode(ex)).isTrue();
    }

    @Test
    void nullExceptionIsNotUnsupported() {
        assertThat(ProxyFallbackPolicy.isUnsupportedRequestCode(null)).isFalse();
    }

    @Test
    void proxyModeIsTrueForV5ProxyTypes() {
        assertThat(ProxyFallbackPolicy.isProxyMode(ClusterType.V5_PROXY_LOCAL)).isTrue();
        assertThat(ProxyFallbackPolicy.isProxyMode(ClusterType.V5_PROXY_CLUSTER)).isTrue();
    }

    @Test
    void proxyModeIsFalseForV4Direct() {
        assertThat(ProxyFallbackPolicy.isProxyMode(ClusterType.V4_DIRECT)).isFalse();
    }

    @Test
    void shouldFallbackCombinesUnsupportedCodeAndProxyMode() {
        MQBrokerException ex = new MQBrokerException(206, "request type 206 not supported");
        assertThat(ProxyFallbackPolicy.shouldFallback(ex, ClusterType.V5_PROXY_CLUSTER)).isTrue();
        assertThat(ProxyFallbackPolicy.shouldFallback(ex, ClusterType.V5_PROXY_LOCAL)).isTrue();
        assertThat(ProxyFallbackPolicy.shouldFallback(ex, ClusterType.V4_DIRECT)).isFalse();
    }

    @Test
    void shouldFallbackIsFalseForUnrelatedErrorInProxyMode() {
        MQBrokerException ex = new MQBrokerException(1, "topic already exists");
        assertThat(ProxyFallbackPolicy.shouldFallback(ex, ClusterType.V5_PROXY_CLUSTER)).isFalse();
    }
}
