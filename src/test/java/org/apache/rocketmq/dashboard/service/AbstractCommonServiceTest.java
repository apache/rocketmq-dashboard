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
package org.apache.rocketmq.dashboard.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link AbstractCommonService#changeToBrokerNameSet}.
 */
public class AbstractCommonServiceTest {

    private final AbstractCommonService service = new AbstractCommonService() {
    };

    private Map<String, Set<String>> clusterAddrTable() {
        Map<String, Set<String>> table = new HashMap<>();
        table.put("DefaultCluster", new HashSet<>(Arrays.asList("broker-a", "broker-b")));
        table.put("SecondCluster", new HashSet<>(Collections.singletonList("broker-c")));
        return table;
    }

    @Test
    public void testResolveBrokerNamesFromClusterList() {
        Set<String> result = service.changeToBrokerNameSet(clusterAddrTable(),
            Collections.singletonList("DefaultCluster"), null);

        assertEquals(new HashSet<>(Arrays.asList("broker-a", "broker-b")), result);
    }

    @Test
    public void testMergeClusterAndBrokerNames() {
        Set<String> result = service.changeToBrokerNameSet(clusterAddrTable(),
            Collections.singletonList("SecondCluster"), Arrays.asList("broker-x", "broker-c"));

        assertEquals(new HashSet<>(Arrays.asList("broker-c", "broker-x")), result);
    }

    @Test
    public void testBrokerNamesOnly() {
        Set<String> result = service.changeToBrokerNameSet(clusterAddrTable(),
            Collections.emptyList(), Collections.singletonList("broker-a"));

        assertEquals(Collections.singleton("broker-a"), result);
    }

    @Test
    public void testEmptyInputsReturnEmptySet() {
        Set<String> result = service.changeToBrokerNameSet(clusterAddrTable(), null, null);

        assertTrue(result.isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testUnknownClusterRethrowsUnchecked() {
        // Unknown cluster leads to a NPE which Throwables.throwIfUnchecked rethrows as-is
        service.changeToBrokerNameSet(clusterAddrTable(),
            Collections.singletonList("NoSuchCluster"), null);
    }
}
