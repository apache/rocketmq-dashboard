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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PrometheusExceptionTest {

    @Test
    void carriesStatusCodeAndMessage() {
        PrometheusException ex = new PrometheusException(502, "upstream unavailable");

        assertEquals(502, ex.getStatusCode());
        assertEquals("upstream unavailable", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void carriesStatusCodeMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        PrometheusException ex = new PrometheusException(500, "scrape failed", cause);

        assertEquals(500, ex.getStatusCode());
        assertEquals("scrape failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
