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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Loads the Grafana dashboard JSON assets bundled under {@code classpath*:grafana/*.json}
 * and exposes them for listing, viewing and exporting.
 */
@Slf4j
@Service
public class GrafanaDashboardService {

    private static final String LOCATION_PATTERN = "classpath*:grafana/*.json";

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver;

    @Autowired
    public GrafanaDashboardService(ObjectMapper objectMapper) {
        this(objectMapper, new PathMatchingResourcePatternResolver());
    }

    GrafanaDashboardService(ObjectMapper objectMapper, ResourcePatternResolver resourceResolver) {
        this.objectMapper = objectMapper;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Lists metadata for every bundled Grafana dashboard.
     */
    public List<GrafanaDashboardInfo> listDashboards() {
        List<GrafanaDashboardInfo> infos = new ArrayList<>();
        for (Resource resource : resolveResources()) {
            String uid = uidOf(resource);
            if (uid == null) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                if (root == null || !root.isObject()) {
                    log.warn("Skipping invalid Grafana dashboard resource {}: expected a JSON object", resource);
                    continue;
                }
                String title = textOr(root, "title", uid);
                String description = textOr(root, "description", "");
                List<String> tags = parseTags(root);
                infos.add(new GrafanaDashboardInfo(uid, title, description, tags));
            } catch (IOException e) {
                log.warn("Skipping unreadable Grafana dashboard resource {}: {}", resource, e.getMessage());
            }
        }
        infos.sort((a, b) -> a.uid().compareTo(b.uid()));
        return infos;
    }

    /**
     * Returns the parsed dashboard model for the given uid.
     *
     * @throws BusinessException with code 404 when the uid is unknown
     */
    public Map<String, Object> getDashboard(String uid) {
        Resource resource = findResource(uid);
        if (resource == null) {
            throw new BusinessException(404, "Grafana dashboard not found: " + uid);
        }
        try (InputStream in = resource.getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> model = objectMapper.readValue(in, Map.class);
            return model;
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read Grafana dashboard: " + uid);
        }
    }

    /**
     * Returns the raw dashboard JSON for the given uid (used for export).
     *
     * @throws BusinessException with code 404 when the uid is unknown
     */
    public String getDashboardJson(String uid) {
        Resource resource = findResource(uid);
        if (resource == null) {
            throw new BusinessException(404, "Grafana dashboard not found: " + uid);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read Grafana dashboard: " + uid);
        }
    }

    /**
     * Returns all valid bundled dashboards as a zip archive. Invalid dashboard assets are skipped
     * the same way as {@link #listDashboards()} so the archive matches the visible dashboard list.
     */
    public byte[] getDashboardsArchive() {
        List<GrafanaDashboardInfo> dashboards = listDashboards();
        if (dashboards.isEmpty()) {
            throw new BusinessException(404, "No Grafana dashboards are available");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (GrafanaDashboardInfo dashboard : dashboards) {
                ZipEntry entry = new ZipEntry(dashboard.uid() + ".json");
                zip.putNextEntry(entry);
                zip.write(getDashboardJson(dashboard.uid()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to export Grafana dashboards");
        }
    }

    private Resource findResource(String uid) {
        for (Resource resource : resolveResources()) {
            if (uid.equals(uidOf(resource))) {
                return resource;
            }
        }
        return null;
    }

    protected Resource[] resolveResources() {
        try {
            return resourceResolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.warn("Unable to resolve Grafana dashboard resources: {}", e.getMessage());
            throw new BusinessException(500, "Failed to resolve bundled Grafana dashboards");
        }
    }

    private static String uidOf(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".json")) {
            return null;
        }
        return filename.substring(0, filename.length() - ".json".length());
    }

    private static String textOr(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() ? node.asText() : fallback;
    }

    private static List<String> parseTags(JsonNode root) {
        JsonNode tags = root.get("tags");
        if (tags == null || !tags.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        tags.forEach(tag -> {
            if (tag.isTextual()) {
                result.add(tag.asText());
            }
        });
        return result;
    }
}
