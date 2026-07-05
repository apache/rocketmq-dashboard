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
package org.apache.rocketmq.dashboard.llm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.BeanAccess;

@Data
public class LlmConfig {

    private String provider;
    private String apiKey;
    private String apiBase;
    private String model = "gpt-4";
    private int maxTokens = 4096;
    private double temperature = 0.0;
    private boolean enabled = false;

    /**
     * Singleton manager for loading and saving LlmConfig from ~/.rmqctl/llm-config.yaml.
     */
    public static class LlmConfigManager {

        private static final Logger log = LoggerFactory.getLogger(LlmConfigManager.class);
        private static final Path CONFIG_FILE =
                Paths.get(System.getProperty("user.home"), ".rmqctl", "llm-config.yaml");

        private static final Yaml YAML;

        static {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            YAML = new Yaml(options);
            YAML.setBeanAccess(BeanAccess.FIELD);
        }

        /**
         * Load LlmConfig from the config file.
         * Returns a default (disabled) config if the file does not exist.
         */
        public static LlmConfig load() {
            File file = CONFIG_FILE.toFile();
            if (!file.exists()) {
                log.debug("LLM config file not found at {}, returning default config", CONFIG_FILE);
                return new LlmConfig();
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                LlmConfig config = YAML.loadAs(fis, LlmConfig.class);
                if (config == null) {
                    config = new LlmConfig();
                }
                return config;
            } catch (IOException e) {
                log.error("Failed to load LLM config from {}: {}", CONFIG_FILE, e.getMessage(), e);
                return new LlmConfig();
            }
        }

        /**
         * Save LlmConfig to the config file. Creates parent directories if needed.
         */
        public static void save(LlmConfig config) throws IOException {
            File file = CONFIG_FILE.toFile();
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                Files.createDirectories(parentDir.toPath());
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                YAML.dump(config, writer);
            }
            log.info("LLM config saved to {}", CONFIG_FILE);
        }

        /**
         * Returns true if the config is enabled and has a non-empty apiKey.
         */
        public static boolean isConfigured() {
            LlmConfig config = load();
            return config.isEnabled()
                    && config.getApiKey() != null
                    && !config.getApiKey().isEmpty();
        }
    }
}
