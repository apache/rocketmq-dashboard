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
package org.apache.rocketmq.studio.cluster.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link PrometheusException}: the typed failure of a Prometheus-compatible backend query.
 * The status code is what distinguishes a backend problem (502) from a query problem (400).
 */
class PrometheusExceptionTest {

    @Test
    void carriesTheStatusCodeAndMessage() {
        PrometheusException exception = new PrometheusException(502, "bad gateway");

        assertThat(exception.getStatusCode()).isEqualTo(502);
        assertThat(exception.getMessage()).isEqualTo("bad gateway");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void theCauseOverloadChainsTheCause() {
        IllegalStateException cause = new IllegalStateException("connection reset");
        PrometheusException exception = new PrometheusException(504, "read timed out", cause);

        assertThat(exception.getStatusCode()).isEqualTo(504);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void aQueryProblemIsDistinguishableFromABackendProblem() {
        PrometheusException query = new PrometheusException(400, "invalid query");
        PrometheusException backend = new PrometheusException(502, "unreachable");

        assertThat(query.getStatusCode()).isEqualTo(400);
        assertThat(backend.getStatusCode()).isEqualTo(502);
    }
}
