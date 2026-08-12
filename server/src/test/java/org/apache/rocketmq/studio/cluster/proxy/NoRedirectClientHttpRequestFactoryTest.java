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
package org.apache.rocketmq.studio.cluster.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class NoRedirectClientHttpRequestFactoryTest {

    @Test
    void prepareConnectionShouldDisableRedirectsTest() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://example.com")
                .toURL().openConnection();
        TestableNoRedirectClientHttpRequestFactory requestFactory =
                new TestableNoRedirectClientHttpRequestFactory();

        assertThat(connection.getInstanceFollowRedirects()).isTrue();

        requestFactory.prepare(connection, HttpMethod.POST.name());

        assertThat(connection.getInstanceFollowRedirects()).isFalse();
        connection.disconnect();
    }

    private static class TestableNoRedirectClientHttpRequestFactory
            extends NoRedirectClientHttpRequestFactory {

        void prepare(HttpURLConnection connection, String httpMethod) throws Exception {
            prepareConnection(connection, httpMethod);
        }
    }
}
