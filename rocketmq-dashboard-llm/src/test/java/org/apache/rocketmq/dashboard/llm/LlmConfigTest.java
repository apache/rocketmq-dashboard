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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LlmConfigTest {

    private static final Path CONFIG_FILE =
            Paths.get(System.getProperty("user.home"), ".rmqctl", "llm-config.yaml");
    private static final Path RMQCTL_DIR = CONFIG_FILE.getParent();

    @Before
    public void setUp() throws IOException {
        // Clean up real config file from previous runs
        Files.deleteIfExists(CONFIG_FILE);
        Files.deleteIfExists(RMQCTL_DIR);
    }

    @After
    public void tearDown() throws IOException {
        // Clean up config file so it doesn't affect subsequent tests
        Files.deleteIfExists(CONFIG_FILE);
        Files.deleteIfExists(RMQCTL_DIR);
    }

    // ---- Default config tests --------------------------------------------------

    @Test
    public void testDefaultConfigValues() {
        LlmConfig config = new LlmConfig();
        assertNull("Default provider should be null", config.getProvider());
        assertNull("Default apiKey should be null", config.getApiKey());
        assertNull("Default apiBase should be null", config.getApiBase());
        assertEquals("Default model should be gpt-4", "gpt-4", config.getModel());
        assertEquals("Default maxTokens should be 4096", 4096, config.getMaxTokens());
        assertEquals("Default temperature should be 0.0", 0.0, config.getTemperature(), 0.001);
        assertFalse("Default enabled should be false", config.isEnabled());
    }

    @Test
    public void testSettersAndGetters() {
        LlmConfig config = new LlmConfig();
        config.setProvider("OPENAI");
        config.setApiKey("sk-test-key-12345");
        config.setApiBase("https://api.openai.com");
        config.setModel("gpt-3.5-turbo");
        config.setMaxTokens(2048);
        config.setTemperature(0.7);
        config.setEnabled(true);

        assertEquals("OPENAI", config.getProvider());
        assertEquals("sk-test-key-12345", config.getApiKey());
        assertEquals("https://api.openai.com", config.getApiBase());
        assertEquals("gpt-3.5-turbo", config.getModel());
        assertEquals(2048, config.getMaxTokens());
        assertEquals(0.7, config.getTemperature(), 0.001);
        assertTrue(config.isEnabled());
    }

    @Test
    public void testDefaultModelCanBeOverridden() {
        LlmConfig config = new LlmConfig();
        config.setModel("deepseek-chat");
        assertEquals("deepseek-chat", config.getModel());
    }

    // ---- ConfigManager: load tests ---------------------------------------------

    @Test
    public void testLoadReturnsDefaultWhenFileDoesNotExist() {
        LlmConfig config = LlmConfig.LlmConfigManager.load();
        assertNotNull("Config should not be null when file is missing", config);
        assertFalse("Config should be disabled by default", config.isEnabled());
        assertNull("API key should be null by default", config.getApiKey());
    }

    @Test
    public void testLoadConfigFromFile() throws IOException {
        writeConfig(
                "provider: DEEPSEEK\n" +
                "apiKey: sk-deepseek-key-abcdef\n" +
                "apiBase: https://api.deepseek.com\n" +
                "model: deepseek-chat\n" +
                "maxTokens: 8192\n" +
                "temperature: 0.3\n" +
                "enabled: true\n");

        LlmConfig loaded = LlmConfig.LlmConfigManager.load();
        assertNotNull("Loaded config should not be null", loaded);
        assertEquals("DEEPSEEK", loaded.getProvider());
        assertEquals("sk-deepseek-key-abcdef", loaded.getApiKey());
        assertEquals("https://api.deepseek.com", loaded.getApiBase());
        assertEquals("deepseek-chat", loaded.getModel());
        assertEquals(8192, loaded.getMaxTokens());
        assertEquals(0.3, loaded.getTemperature(), 0.001);
        assertTrue(loaded.isEnabled());
    }

    @Test
    public void testSaveCreatesParentDirectories() throws IOException {
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");

        LlmConfig.LlmConfigManager.save(config);

        assertTrue("Directory .rmqctl should exist", RMQCTL_DIR.toFile().exists());
        assertTrue("Should be a directory", RMQCTL_DIR.toFile().isDirectory());
        assertTrue("Config file should exist", CONFIG_FILE.toFile().exists());
    }

    @Test
    public void testLoadConfigOverwritesCorrectly() throws IOException {
        writeConfig(
                "provider: OPENAI\n" +
                "apiKey: key-one\n" +
                "enabled: true\n");

        LlmConfig loaded1 = LlmConfig.LlmConfigManager.load();
        assertEquals("OPENAI", loaded1.getProvider());

        // Overwrite with new content
        writeConfig(
                "provider: DEEPSEEK\n" +
                "apiKey: key-two\n" +
                "enabled: true\n");

        LlmConfig loaded2 = LlmConfig.LlmConfigManager.load();
        assertEquals("DEEPSEEK", loaded2.getProvider());
        assertEquals("key-two", loaded2.getApiKey());
    }

    @Test
    public void testLoadConfigWithAllFields() throws IOException {
        writeConfig(
                "provider: AZURE\n" +
                "apiKey: azure-api-key\n" +
                "apiBase: https://my-azure.openai.azure.com\n" +
                "model: gpt-4\n" +
                "maxTokens: 1024\n" +
                "temperature: 0.0\n" +
                "enabled: true\n");

        LlmConfig loaded = LlmConfig.LlmConfigManager.load();
        assertEquals("AZURE", loaded.getProvider());
        assertEquals("azure-api-key", loaded.getApiKey());
        assertEquals("https://my-azure.openai.azure.com", loaded.getApiBase());
        assertEquals("gpt-4", loaded.getModel());
        assertEquals(1024, loaded.getMaxTokens());
        assertEquals(0.0, loaded.getTemperature(), 0.001);
        assertTrue(loaded.isEnabled());
    }

    // ---- ConfigManager: isConfigured tests -------------------------------------

    @Test
    public void testIsConfiguredReturnsFalseWhenDisabled() throws IOException {
        writeConfig("apiKey: sk-key\n" + "enabled: false\n");
        assertFalse(LlmConfig.LlmConfigManager.isConfigured());
    }

    @Test
    public void testIsConfiguredReturnsFalseWhenApiKeyIsNull() throws IOException {
        writeConfig("enabled: true\n");
        assertFalse(LlmConfig.LlmConfigManager.isConfigured());
    }

    @Test
    public void testIsConfiguredReturnsFalseWhenApiKeyIsEmpty() throws IOException {
        writeConfig("apiKey: ''\n" + "enabled: true\n");
        assertFalse(LlmConfig.LlmConfigManager.isConfigured());
    }

    @Test
    public void testIsConfiguredReturnsTrueWhenEnabledWithApiKey() throws IOException {
        writeConfig("enabled: true\n" + "apiKey: sk-valid-key\n");
        assertTrue(LlmConfig.LlmConfigManager.isConfigured());
    }

    @Test
    public void testIsConfiguredReturnsFalseWhenFileMissing() {
        assertFalse(LlmConfig.LlmConfigManager.isConfigured());
    }

    // ---- Edge case tests -------------------------------------------------------

    @Test
    public void testLoadConfigWithNullProvider() throws IOException {
        writeConfig("enabled: true\n" + "apiKey: test-key\n");
        LlmConfig loaded = LlmConfig.LlmConfigManager.load();
        assertNull("Provider should be null when omitted", loaded.getProvider());
        assertTrue(loaded.isEnabled());
    }

    @Test
    public void testLoadConfigWithSpecialCharactersInApiKey() throws IOException {
        writeConfig(
                "enabled: true\n" +
                "apiKey: key-with-special-chars\n" +
                "provider: CUSTOM_PROVIDER\n");

        LlmConfig loaded = LlmConfig.LlmConfigManager.load();
        assertEquals("key-with-special-chars", loaded.getApiKey());
    }

    @Test
    public void testSaveMultipleTimesWritesFile() throws IOException {
        // Test that save() writes the file multiple times
        for (int i = 0; i < 3; i++) {
            LlmConfig config = new LlmConfig();
            config.setEnabled(true);
            config.setApiKey("key-round-" + i);
            config.setProvider("PROVIDER-" + i);
            LlmConfig.LlmConfigManager.save(config);
            assertTrue("Config file should exist after save " + i,
                    CONFIG_FILE.toFile().exists());
        }
    }

    // ---- Load config with null apiKey test ------------------------------------

    @Test
    public void testLoadConfigWithNullApiKey() throws IOException {
        writeConfig("enabled: true\n" + "provider: OPENAI\n");
        LlmConfig loaded = LlmConfig.LlmConfigManager.load();
        assertNull(loaded.getApiKey());
    }

    // ---- Helper methods --------------------------------------------------------

    private void writeConfig(String yamlContent) throws IOException {
        Files.createDirectories(RMQCTL_DIR);
        Files.write(CONFIG_FILE, yamlContent.getBytes(StandardCharsets.UTF_8));
    }
}
