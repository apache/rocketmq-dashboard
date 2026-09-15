/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.dashboard;

import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardSummaryToolHandlerTest {

    @Test
    void instanceSummaryIncludesAllPhysicalClusters() {
        DashboardService dashboardService = mock(DashboardService.class);
        DashboardSummaryToolHandler handler = new DashboardSummaryToolHandler(dashboardService);
        DashboardDataVO dashboard = DashboardDataVO.builder()
                .clusters(List.of(
                        ClusterOverviewVO.builder().id("cluster-a").build(),
                        ClusterOverviewVO.builder().id("cluster-b").build()))
                .build();
        when(dashboardService.getDashboard("instance-a")).thenReturn(dashboard);

        var result = handler.execute(new ClusterInput("instance-a"), context("instance-a"));
        assertThat(result.cluster()).isEqualTo("instance-a");
        assertThat(result.clusters()).extracting(DashboardSummaryToolHandler.Cluster::id)
                .containsExactly("cluster-a", "cluster-b");
    }
}
