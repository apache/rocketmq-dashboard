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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AlertRuleAssetInfoTest {

    @Test
    void exposesAllRecordComponents() {
        AlertRuleAssetInfo info = new AlertRuleAssetInfo("disk-high", "DefaultCluster", 3,
                List.of("critical", "warning"));

        assertEquals("disk-high", info.name());
        assertEquals("DefaultCluster", info.group());
        assertEquals(3, info.ruleCount());
        assertEquals(List.of("critical", "warning"), info.severities());
    }

    @Test
    void equalityFollowsRecordComponents() {
        AlertRuleAssetInfo a = new AlertRuleAssetInfo("disk-high", "DefaultCluster", 3,
                List.of("critical"));
        AlertRuleAssetInfo same = new AlertRuleAssetInfo("disk-high", "DefaultCluster", 3,
                List.of("critical"));
        AlertRuleAssetInfo different = new AlertRuleAssetInfo("lag-high", "DefaultCluster", 3,
                List.of("critical"));

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
