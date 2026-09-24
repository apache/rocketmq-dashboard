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
package org.apache.rocketmq.studio.cluster.broker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerDiskWatermarkForecasterTest {

    private BrokerDiskWatermarkForecaster forecaster;

    @BeforeEach
    void setUp() {
        forecaster = new BrokerDiskWatermarkForecaster();
    }

    @Test
    void testForecastEmptyBrokers() {
        BrokerDiskForecasterReportVO report = forecaster.forecast("cluster-a", Collections.emptyList());
        assertNotNull(report);
        assertEquals(0, report.getTotalBrokers());
        assertFalse(report.isAnyBrokerBlocked());
        assertTrue(report.getOperationalAlerts().get(0).contains("No broker instances available"));
    }

    @Test
    void testForecastNormalWatermark() {
        BrokerVO broker = BrokerVO.builder()
                .name("broker-a")
                .addr("10.0.0.1:10911")
                .diskUsage(0.45) // 45%
                .putMessagesToday(10000)
                .putMessagesYesterday(8000)
                .build();

        BrokerDiskForecasterReportVO report = forecaster.forecast("cluster-a", List.of(broker));
        assertNotNull(report);
        assertEquals(1, report.getTotalBrokers());
        assertEquals(0, report.getCriticalWatermarkBrokerCount());
        assertFalse(report.isAnyBrokerBlocked());

        BrokerDiskForecasterReportVO.BrokerDiskAssessmentVO assessment = report.getBrokerAssessments().get(0);
        assertEquals("NORMAL", assessment.getWatermarkLevel());
        assertFalse(assessment.isWriteBlocked());
        assertEquals(168L, assessment.getEstimatedHoursToShutdown());
        assertEquals(2000L, assessment.getDailyMessageIngestGrowth());
        assertTrue(report.getOperationalAlerts().get(0).contains("normal operating safety limits"));
    }

    @Test
    void testForecastWarningAndCleanWatermarks() {
        BrokerVO b1 = BrokerVO.builder().name("b1").addr("10.0.0.1:10911").diskUsage(0.78).build(); // CLEAN_RESOURCE
        BrokerVO b2 = BrokerVO.builder().name("b2").addr("10.0.0.2:10911").diskUsage(88.0).build(); // WARN (percentage form)

        BrokerDiskForecasterReportVO report = forecaster.forecast("cluster-a", List.of(b1, b2));
        assertNotNull(report);
        assertEquals(2, report.getTotalBrokers());
        assertEquals("CLEAN_RESOURCE", report.getBrokerAssessments().get(0).getWatermarkLevel());
        assertEquals("WARN", report.getBrokerAssessments().get(1).getWatermarkLevel());
        assertFalse(report.isAnyBrokerBlocked());
    }

    @Test
    void testForecastDangerousAndShutdownBlockedWatermarks() {
        BrokerVO bDanger = BrokerVO.builder()
                .name("b-danger")
                .addr("10.0.0.1:10911")
                .diskUsage(0.92)
                .tpsIn(100)
                .putMessagesToday(200_000)
                .putMessagesYesterday(50_000)
                .build(); // DANGEROUS
        BrokerVO bShutdown = BrokerVO.builder()
                .name("b-shutdown")
                .addr("10.0.0.2:10911")
                .diskUsage(0.96)
                .build(); // SHUTDOWN

        BrokerDiskForecasterReportVO report = forecaster.forecast("cluster-a", List.of(bDanger, bShutdown));
        assertNotNull(report);
        assertEquals(2, report.getTotalBrokers());
        assertEquals(2, report.getCriticalWatermarkBrokerCount());
        assertTrue(report.isAnyBrokerBlocked());

        BrokerDiskForecasterReportVO.BrokerDiskAssessmentVO dangerAssessment = report.getBrokerAssessments().get(0);
        assertEquals("DANGEROUS", dangerAssessment.getWatermarkLevel());
        assertTrue(dangerAssessment.isWriteBlocked());
        assertTrue(dangerAssessment.getEstimatedHoursToShutdown() <= 2L);

        BrokerDiskForecasterReportVO.BrokerDiskAssessmentVO shutdownAssessment = report.getBrokerAssessments().get(1);
        assertEquals("SHUTDOWN", shutdownAssessment.getWatermarkLevel());
        assertTrue(shutdownAssessment.isWriteBlocked());
        assertEquals(0L, shutdownAssessment.getEstimatedHoursToShutdown());

        assertTrue(report.getOperationalAlerts().get(0).contains("BLOCKING ALERT"));
    }

    @Test
    void testForecastBoundaryRatios() {
        BrokerVO bZero = BrokerVO.builder().name("b0").addr("10.0.0.1:10911").diskUsage(-0.1).build();
        BrokerVO bMax = BrokerVO.builder().name("bMax").addr("10.0.0.2:10911").diskUsage(1.5).build();

        BrokerDiskForecasterReportVO report = forecaster.forecast("cluster-bounds", List.of(bZero, bMax));
        assertNotNull(report);
        assertEquals(0.0, report.getBrokerAssessments().get(0).getCurrentDiskUsageRatio());
        assertEquals(1.0, report.getBrokerAssessments().get(1).getCurrentDiskUsageRatio());
        assertEquals("SHUTDOWN", report.getBrokerAssessments().get(1).getWatermarkLevel());
    }
}
