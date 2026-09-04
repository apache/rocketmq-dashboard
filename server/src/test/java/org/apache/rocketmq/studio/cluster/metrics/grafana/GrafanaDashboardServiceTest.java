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
package org.apache.rocketmq.studio.cluster.metrics.grafana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrafanaDashboardServiceTest {

    private final GrafanaDashboardService service =
            new GrafanaDashboardService(new ObjectMapper());

    @Test
    void listsBundledDashboardsSortedByUid() {
        List<GrafanaDashboardInfo> dashboards = service.listDashboards();

        assertThat(dashboards).isNotEmpty();
        assertThat(dashboards).isSortedAccordingTo((left, right) -> left.uid().compareTo(right.uid()));
        for (GrafanaDashboardInfo dashboard : dashboards) {
            assertThat(dashboard.title()).isNotBlank();
        }
    }

    @Test
    void returnsDashboardModelAndRawJsonForKnownUid() {
        String uid = service.listDashboards().get(0).uid();

        assertThat(service.getDashboard(uid)).containsKey("title");
        assertThat(service.getDashboardJson(uid)).contains("\"uid\"");
    }

    @Test
    void rejectsUnknownUidsWith404() {
        assertThatThrownBy(() -> service.getDashboardJson("no-such-dashboard"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void exportsAllValidDashboardsAsZip() {
        byte[] archive = service.getDashboardsArchive();

        assertThat(archive).startsWith((byte) 'P', (byte) 'K');
        String sample = new String(archive, StandardCharsets.ISO_8859_1);
        for (GrafanaDashboardInfo dashboard : service.listDashboards()) {
            assertThat(sample).contains(dashboard.uid() + ".json");
        }
    }
}
