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
package org.apache.rocketmq.dashboard.cli.schema;

import org.junit.Assert;
import org.junit.Test;

public class RiskLevelTest {

    @Test
    public void testEnumValues() {
        RiskLevel[] values = RiskLevel.values();
        Assert.assertEquals(3, values.length);
        Assert.assertEquals(RiskLevel.L1, RiskLevel.valueOf("L1"));
        Assert.assertEquals(RiskLevel.L2, RiskLevel.valueOf("L2"));
        Assert.assertEquals(RiskLevel.L3, RiskLevel.valueOf("L3"));
    }

    @Test
    public void testGetLabel() {
        Assert.assertNotNull(RiskLevel.L1.getLabel());
        Assert.assertFalse(RiskLevel.L1.getLabel().isEmpty());
        Assert.assertNotNull(RiskLevel.L2.getLabel());
        Assert.assertFalse(RiskLevel.L2.getLabel().isEmpty());
        Assert.assertNotNull(RiskLevel.L3.getLabel());
        Assert.assertFalse(RiskLevel.L3.getLabel().isEmpty());
    }

    @Test
    public void testGetDescription() {
        Assert.assertNotNull(RiskLevel.L1.getDescription());
        Assert.assertFalse(RiskLevel.L1.getDescription().isEmpty());
        Assert.assertNotNull(RiskLevel.L2.getDescription());
        Assert.assertFalse(RiskLevel.L2.getDescription().isEmpty());
        Assert.assertNotNull(RiskLevel.L3.getDescription());
        Assert.assertFalse(RiskLevel.L3.getDescription().isEmpty());
    }

    @Test
    public void testLabelsReadOnly() {
        Assert.assertEquals("Read-only", RiskLevel.L1.getLabel());
        Assert.assertEquals("Controlled mutation", RiskLevel.L2.getLabel());
        Assert.assertEquals("Dangerous operation", RiskLevel.L3.getLabel());
    }
}
