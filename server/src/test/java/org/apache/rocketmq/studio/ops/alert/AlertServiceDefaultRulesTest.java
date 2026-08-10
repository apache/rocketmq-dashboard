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

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertServiceDefaultRulesTest {

    private static final Pattern ALERT_PATTERN = Pattern.compile("^\\s*- alert:");

    @Test
    void exportUsesBundledAssetsAsDefaultWhenRepositoryIsEmpty() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.findAllRules()).thenReturn(Collections.emptyList());

        AlertService service = new AlertService(repository, new AlertRuleAssetService(),
                Mockito.mock(OperationAuditService.class));
        String yaml = service.exportPrometheusRulesYaml();

        int ruleCount = countRules(yaml);
        assertTrue(ruleCount >= 20, "expected at least 20 default alert rules, got " + ruleCount);
    }

    @Test
    void listRulesDelegatesToRepositoryWhenEmptyUsesDefaultsViaExport() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.findAllRules()).thenReturn(List.of());

        AlertService service = new AlertService(repository, new AlertRuleAssetService(),
                Mockito.mock(OperationAuditService.class));
        String yaml = service.exportPrometheusRulesYaml();

        assertTrue(yaml.contains("rocketmq-broker.rules"));
        assertTrue(yaml.contains("RocketMQBrokerDown"));
    }

    private int countRules(String yaml) {
        int count = 0;
        for (String line : yaml.split("\n")) {
            Matcher matcher = ALERT_PATTERN.matcher(line);
            if (matcher.find()) {
                count++;
            }
        }
        return count;
    }
}
