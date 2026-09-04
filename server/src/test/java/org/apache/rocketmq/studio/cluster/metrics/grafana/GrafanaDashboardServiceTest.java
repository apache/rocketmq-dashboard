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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void getDashboardShouldRejectNullJsonAsset() {
        GrafanaDashboardService service = serviceWithResources(resource("null.json", "null"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getDashboard("null"));

        assertEquals(500, exception.getCode());
        assertEquals("Failed to read Grafana dashboard: null", exception.getMessage());
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
    void getDashboardJsonShouldDecodeBundledAssetAsUtf8() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("grafana/rocketmq-broker.json")) {
            assertTrue(input != null, "expected bundled dashboard resource");
            String expected = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(expected, service.getDashboardJson("rocketmq-broker"));
        }
    }

    @Test
    void getDashboardJsonShouldThrowWhenUidUnknown() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getDashboardJson("no-such-dashboard"));
        assertEquals(404, exception.getCode());
    }

    @Test
    void getDashboardsArchiveShouldIncludeAllVisibleDashboards() throws Exception {
        GrafanaDashboardService service = serviceWithResources(
                resource("b.json", "{\"uid\":\"b\",\"title\":\"B\",\"tags\":[\"rocketmq\"]}"),
                resource("invalid.json", "[]"),
                resource("a.json", "{\"uid\":\"a\",\"title\":\"A\",\"tags\":[\"rocketmq\"]}"));

        byte[] archive = service.getDashboardsArchive();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            assertEquals("a.json", zip.getNextEntry().getName());
            assertTrue(new String(zip.readAllBytes(), StandardCharsets.UTF_8).contains("\"uid\":\"a\""));
            assertEquals("b.json", zip.getNextEntry().getName());
            assertTrue(new String(zip.readAllBytes(), StandardCharsets.UTF_8).contains("\"uid\":\"b\""));
            assertNull(zip.getNextEntry());
        }
    }

    @Test
    void getDashboardsArchiveShouldRejectEmptyDashboardSet() {
        GrafanaDashboardService service = serviceWithResources(resource("invalid.json", "[]"));

        BusinessException exception = assertThrows(BusinessException.class, service::getDashboardsArchive);

        assertEquals(404, exception.getCode());
    }

    @Test
    void listDashboardsShouldSkipEmptyAndNonObjectAssets() {
        GrafanaDashboardService service = serviceWithResources(
                resource("empty.json", ""),
                resource("array.json", "[]"),
                resource("valid.json", "{\"title\":\"Valid\",\"tags\":[\"rocketmq\"]}"));

        List<GrafanaDashboardInfo> dashboards = service.listDashboards();

        assertEquals(List.of(new GrafanaDashboardInfo("valid", "Valid", "", List.of("rocketmq"))), dashboards);
    }

    @Test
    void listDashboardsShouldSurfaceResourceDiscoveryFailure() throws Exception {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources("classpath*:grafana/*.json")).thenThrow(new java.io.IOException("broken jar"));
        GrafanaDashboardService service = new GrafanaDashboardService(new ObjectMapper(), resolver);

        BusinessException exception = assertThrows(BusinessException.class, service::listDashboards);

        assertEquals(500, exception.getCode());
        assertTrue(exception.getMessage().contains("resolve bundled Grafana dashboards"));
    }

    @Test
    void dashboardOperationsShouldDeterministicallyDeduplicateUid() throws Exception {
        GrafanaDashboardService service = serviceWithResources(
                resource("duplicate.json", "z-location", "{\"title\":\"Second\"}"),
                resource("duplicate.json", "a-location", "{\"title\":\"First\"}"));

        assertEquals(List.of(new GrafanaDashboardInfo("duplicate", "First", "", List.of())),
                service.listDashboards());
        assertEquals("First", service.getDashboard("duplicate").get("title"));

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(service.getDashboardsArchive()), StandardCharsets.UTF_8)) {
            assertEquals("duplicate.json", zip.getNextEntry().getName());
            assertTrue(new String(zip.readAllBytes(), StandardCharsets.UTF_8).contains("First"));
            assertNull(zip.getNextEntry());
        }
    }

    private static GrafanaDashboardService serviceWithResources(Resource... resources) {
        return new GrafanaDashboardService(new ObjectMapper()) {
            @Override
            protected Resource[] resolveResources() {
                return resources;
            }
        };
    }

    private static Resource resource(String filename, String content) {
        return resource(filename, filename, content);
    }

    private static Resource resource(String filename, String description, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public String getDescription() {
                return description;
            }
        };
    }
}
