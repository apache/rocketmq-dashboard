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
package org.apache.rocketmq.studio.cluster.metrics.grafana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrafanaDashboardServiceTest {

    private final GrafanaDashboardService service = new GrafanaDashboardService(new ObjectMapper());

    @Test
    void listDashboardsShouldExposeBundledAssets() {
        List<GrafanaDashboardInfo> dashboards = service.listDashboards();

        assertFalse(dashboards.isEmpty(), "expected bundled dashboards to be present");
        assertTrue(dashboards.size() >= 10, "expected at least 10 dashboards, got " + dashboards.size());

        for (GrafanaDashboardInfo info : dashboards) {
            assertFalse(info.uid().isBlank(), "dashboard uid must not be blank");
            assertFalse(info.title().isBlank(), "dashboard title must not be blank");
            assertTrue(info.tags().contains("rocketmq"), "dashboard should be tagged rocketmq");
        }
    }

    @Test
    void getDashboardShouldReturnParsedModel() {
        Map<String, Object> model = service.getDashboard("rocketmq-overview");

        assertEquals("rocketmq-overview", model.get("uid"));
        assertEquals("RocketMQ Cluster Overview", model.get("title"));
        assertTrue(model.containsKey("panels"), "dashboard should contain panels");
    }

    @Test
    void getDashboardShouldThrowWhenUidUnknown() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getDashboard("no-such-dashboard"));
        assertEquals(404, exception.getCode());
    }

    @Test
    void getDashboardJsonShouldReturnRawContent() {
        String json = service.getDashboardJson("rocketmq-broker");

        assertFalse(json.isBlank());
        assertTrue(json.contains("\"uid\""));
        assertTrue(json.contains("rocketmq-broker"));
    }

    @Test
    void getDashboardJsonShouldThrowWhenUidUnknown() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getDashboardJson("no-such-dashboard"));
        assertEquals(404, exception.getCode());
    }
}
