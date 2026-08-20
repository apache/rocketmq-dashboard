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

package org.apache.rocketmq.studio.common.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Region display names loaded once at application startup from regions.properties
 * (baseline: RocketMQ 5.0 region list). Unknown or blank region ids resolve to themselves.
 */
@Slf4j
@Component
public class RegionNames {

    private static final String CONFIG_FILE = "regions.properties";

    private Map<String, String> names = Collections.emptyMap();

    @PostConstruct
    void load() {
        Properties properties = new Properties();
        try (InputStream input = new ClassPathResource(CONFIG_FILE).getInputStream()) {
            properties.load(input);
        } catch (IOException ex) {
            log.warn("Failed to load {}, region names fall back to raw ids: {}", CONFIG_FILE, ex.getMessage());
            return;
        }
        Map<String, String> loaded = new HashMap<>();
        properties.forEach((key, value) -> {
            if (key != null && value != null && !value.toString().isBlank()) {
                loaded.put(key.toString().trim(), value.toString().trim());
            }
        });
        names = Collections.unmodifiableMap(loaded);
        log.info("Loaded {} region display names from {}", names.size(), CONFIG_FILE);
    }

    public String resolve(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return regionId;
        }
        return names.getOrDefault(regionId.trim(), regionId);
    }
}
