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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InMemoryBranchCompensateAlertRuleRepository implements BranchCompensateAlertRuleRepository {

    private final Map<String, BranchCompensateAlertRuleVO> rules = new ConcurrentHashMap<>();

    @Override
    public List<BranchCompensateAlertRuleVO> findAllRules() {
        return new ArrayList<>(rules.values());
    }

    @Override
    public BranchCompensateAlertRuleVO saveRule(BranchCompensateAlertRuleVO rule) {
        rules.put(rule.getId(), rule);
        log.debug("Saved branch compensate alert rule id={}", rule.getId());
        return rule;
    }

    @Override
    public void deleteRule(String id) {
        rules.remove(id);
        log.debug("Deleted branch compensate alert rule id={}", id);
    }

    @Override
    public BranchCompensateAlertRuleVO findRuleById(String id) {
        return rules.get(id);
    }
}