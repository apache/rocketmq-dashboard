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

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void parseRulesShouldMapSeverityAndTeamLabels() {
        List<PrometheusAlertRule> rules = service.loadDefaultRules();
        boolean hasCritical = rules.stream().anyMatch(r -> "critical".equals(r.severity()));
        boolean hasBroker = rules.stream().anyMatch(r -> "broker".equals(r.team()));
        assertTrue(hasCritical, "expected at least one critical rule");
        assertTrue(hasBroker, "expected at least one broker rule");
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

    private static AlertRuleAssetService serviceWithResources(Resource... resources) {
        return new AlertRuleAssetService() {
            @Override
            protected Resource[] resolveResources() {
                return resources;
            }
        };
    }

    private static Resource resource(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
