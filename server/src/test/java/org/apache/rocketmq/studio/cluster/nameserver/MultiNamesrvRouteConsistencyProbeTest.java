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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiNamesrvRouteConsistencyProbeTest {

    private MultiNamesrvRouteConsistencyProbe probe;

    @BeforeEach
    void setUp() {
        probe = new MultiNamesrvRouteConsistencyProbe();
    }

    @Test
    void testProbeEmptyNodeMap() {
        NamesrvRouteConsistencyReportVO report = probe.probe("TopicTest", Collections.emptyMap());
        assertNotNull(report);
        assertFalse(report.isFullyConsistent());
        assertEquals(0, report.getTotalNameServersQueried());
        assertTrue(report.getWarnings().get(0).contains("No NameServer endpoints"));
    }

    @Test
    void testProbeSingleNameServer() {
        TopicRouteData route = new TopicRouteData();
        BrokerData bd = new BrokerData();
        bd.setBrokerName("broker-a");
        route.setBrokerDatas(List.of(bd));

        NamesrvRouteConsistencyReportVO report = probe.probe("TopicTest", Map.of("10.0.0.1:9876", route));
        assertNotNull(report);
        assertTrue(report.isFullyConsistent());
        assertEquals(1, report.getTotalNameServersQueried());
        assertEquals(1, report.getSuccessfulNameServers());
        assertTrue(report.getWarnings().get(0).contains("Only single reachable NameServer"));
    }

    @Test
    void testProbeConsistentMultiNameServers() {
        TopicRouteData route1 = createRoute("broker-a", 8);
        TopicRouteData route2 = createRoute("broker-a", 8);

        Map<String, TopicRouteData> map = new LinkedHashMap<>();
        map.put("10.0.0.1:9876", route1);
        map.put("10.0.0.2:9876", route2);

        NamesrvRouteConsistencyReportVO report = probe.probe("TopicOrders", map);
        assertNotNull(report);
        assertTrue(report.isFullyConsistent());
        assertEquals(2, report.getSuccessfulNameServers());
        assertTrue(report.getRouteDrifts().isEmpty());
        assertTrue(report.getWarnings().get(0).contains("100% identical topic routing topologies"));
    }

    @Test
    void testProbeBrokerMembershipDrift() {
        TopicRouteData route1 = createRoute("broker-a", 8);

        TopicRouteData route2 = new TopicRouteData();
        BrokerData bd1 = new BrokerData();
        bd1.setBrokerName("broker-a");
        BrokerData bd2 = new BrokerData();
        bd2.setBrokerName("broker-b"); // Drift: broker-b registered only on nameserver 2
        route2.setBrokerDatas(List.of(bd1, bd2));
        QueueData qd = new QueueData();
        qd.setWriteQueueNums(8);
        route2.setQueueDatas(List.of(qd));

        Map<String, TopicRouteData> map = new LinkedHashMap<>();
        map.put("10.0.0.1:9876", route1);
        map.put("10.0.0.2:9876", route2);

        NamesrvRouteConsistencyReportVO report = probe.probe("TopicDrift", map);
        assertNotNull(report);
        assertFalse(report.isFullyConsistent());
        assertEquals(1, report.getRouteDrifts().size());
        assertEquals("BROKER_DATA_MEMBERSHIP", report.getRouteDrifts().get(0).getDimension());
        assertEquals("10.0.0.2:9876", report.getRouteDrifts().get(0).getDeviantNamesrvAddr());
    }

    @Test
    void testProbeQueueCountMismatch() {
        TopicRouteData route1 = createRoute("broker-a", 8);
        TopicRouteData route2 = createRoute("broker-a", 16); // Drift: queue count 8 vs 16

        Map<String, TopicRouteData> map = new LinkedHashMap<>();
        map.put("10.0.0.1:9876", route1);
        map.put("10.0.0.2:9876", route2);

        NamesrvRouteConsistencyReportVO report = probe.probe("TopicQueues", map);
        assertNotNull(report);
        assertFalse(report.isFullyConsistent());
        assertEquals(1, report.getRouteDrifts().size());
        assertEquals("QUEUE_COUNT_MISMATCH", report.getRouteDrifts().get(0).getDimension());
    }

    private TopicRouteData createRoute(String brokerName, int writeQueues) {
        TopicRouteData route = new TopicRouteData();
        BrokerData bd = new BrokerData();
        bd.setBrokerName(brokerName);
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.10:10911");
        bd.setBrokerAddrs(addrs);
        route.setBrokerDatas(List.of(bd));

        QueueData qd = new QueueData();
        qd.setBrokerName(brokerName);
        qd.setWriteQueueNums(writeQueues);
        qd.setReadQueueNums(writeQueues);
        qd.setPerm(6);
        route.setQueueDatas(List.of(qd));
        return route;
    }
}
