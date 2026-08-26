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
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertRuleAssetServiceTest {

    private final AlertRuleAssetService service =
            new AlertRuleAssetService();

    @Test
    void listAssetsShouldExposeBundledYamlFiles() {
        List<AlertRuleAssetInfo> assets = service.listAssets();

        assertFalse(assets.isEmpty(), "expected bundled alert rule assets");
        assertTrue(assets.size() >= 10, "expected at least 10 asset files, got " + assets.size());
        for (AlertRuleAssetInfo asset : assets) {
            assertFalse(asset.name().isBlank());
            assertTrue(asset.ruleCount() >= 1, "asset should contain at least one rule");
        }
    }

    @Test
    void loadDefaultRulesShouldReturnAtLeastTwentyRules() {
        List<PrometheusAlertRule> rules = service.loadDefaultRules();

        assertTrue(rules.size() >= 20, "expected at least 20 default alert rules, got " + rules.size());
        for (PrometheusAlertRule rule : rules) {
            assertFalse(rule.alert().isBlank(), "rule alert name must not be blank");
            assertFalse(rule.expr().isBlank(), "rule expr must not be blank");
        }
    }

    @Test
    void getAssetYamlShouldReturnRawContent() {
        List<AlertRuleAssetInfo> assets = service.listAssets();
        String name = assets.get(0).name();

        String yaml = service.getAssetYaml(name);

        assertFalse(yaml.isBlank());
        assertTrue(yaml.contains("alert:"));
        assertTrue(yaml.contains("expr:"));
    }

    @Test
    void getAssetYamlShouldThrowWhenNameUnknown() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getAssetYaml("no-such-asset"));
        assertEquals(404, exception.getCode());
    }

    @Test
    void listAssetsShouldSurfaceResourceDiscoveryFailures() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenThrow(new IOException("classpath unavailable"));
        AlertRuleAssetService failingService = new AlertRuleAssetService(resolver);

        BusinessException exception = assertThrows(BusinessException.class, failingService::listAssets);

        assertEquals(500, exception.getCode());
        assertEquals("Failed to resolve bundled alert rule assets", exception.getMessage());
    }

    @Test
    void parseRulesShouldMapSeverityAndTeamLabels() {
        List<PrometheusAlertRule> rules = service.loadDefaultRules();
        boolean hasCritical = rules.stream().anyMatch(r -> "critical".equals(r.severity()));
        boolean hasBroker = rules.stream().anyMatch(r -> "broker".equals(r.team()));
        assertTrue(hasCritical, "expected at least one critical rule");
        assertTrue(hasBroker, "expected at least one broker rule");
    }

    @Test
    void clientConnectionDropRuleShouldUseSignedGaugeDelta() {
        PrometheusAlertRule rule = service.loadDefaultRules().stream()
                .filter(r -> "RocketMQClientConnectionDrop".equals(r.alert()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected bundled client connection drop rule"));

        assertEquals("delta(rocketmq_producer_count[5m]) < -5", rule.expr());
        assertFalse(rule.expr().contains("changes(rocketmq_producer_count[5m]) < -5"),
                "changes() counts value transitions and cannot produce a negative drop");
    }

    @Test
    void generatorShouldUseSameTriggerableClientConnectionDropExpression() throws IOException {
        String generator = Files.readString(Path.of("scripts", "gen_alert_rule_yaml.py"));

        assertTrue(generator.contains("'delta(rocketmq_producer_count[5m]) < -5'"));
        assertFalse(generator.contains("'changes(rocketmq_producer_count[5m]) < -5'"),
                "generator must not recreate a non-triggerable changes() drop rule");
    }

    @Test
    void assetLoadingShouldSkipEmptyAndNonObjectYaml() {
        AlertRuleAssetService service = serviceWithResources(
                resource("empty.yaml", ""),
                resource("array.yaml", "[]"),
                resource("valid.yaml", "groups:\n  - name: broker\n    rules:\n      - alert: BrokerDown\n        expr: up == 0\n"));

        assertEquals(List.of(new AlertRuleAssetInfo("valid", "broker", 1, List.of("warning"))),
                service.listAssets());
        assertEquals(List.of(new PrometheusAlertRule("broker", "BrokerDown", "up == 0", "5m",
                "warning", "broker", "BrokerDown", "")), service.loadDefaultRules());
    }

    @Test
    void assetLoadingShouldSkipRulesWithoutAlertOrExpression() {
        AlertRuleAssetService service = serviceWithResources(resource("mixed.yaml", """
                groups:
                  - name: broker
                    rules:
                      - alert: MissingExpression
                      - expr: up == 0
                      - alert: BrokerDown
                        expr: up == 0
                """));

        assertEquals(List.of(new AlertRuleAssetInfo("mixed", "broker", 1, List.of("warning"))),
                service.listAssets());
        assertEquals(List.of(new PrometheusAlertRule("broker", "BrokerDown", "up == 0", "5m",
                "warning", "broker", "BrokerDown", "")), service.loadDefaultRules());
    }

    @Test
    void assetOperationsShouldDeterministicallyDeduplicateName() {
        AlertRuleAssetService service = serviceWithResources(
                resource("duplicate.yaml", "z-location", "groups:\n  - name: second\n    rules:\n"
                        + "      - alert: SecondRule\n        expr: up == 2\n"),
                resource("duplicate.yaml", "a-location", "groups:\n  - name: first\n    rules:\n"
                        + "      - alert: FirstRule\n        expr: up == 1\n"));

        assertEquals(List.of(new AlertRuleAssetInfo("duplicate", "first", 1, List.of("warning"))),
                service.listAssets());
        assertEquals(List.of("FirstRule"), service.loadDefaultRules().stream()
                .map(PrometheusAlertRule::alert)
                .toList());
        assertTrue(service.getAssetYaml("duplicate").contains("FirstRule"));
    }

    private static AlertRuleAssetService serviceWithResources(Resource... resources) {
        return new AlertRuleAssetService() {
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
