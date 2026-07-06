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
package com.rocketmq.studio.cluster.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PrometheusMetricsSource implements MetricsSource {

    @Override
    public MetricDataVO query(MetricQueryDTO query) {
        log.info("Querying Prometheus: metric={}, start={}, end={}, step={}",
                query.getMetric(), query.getStart(), query.getEnd(), query.getStep());

        // Stub: generate sample data points
        List<long[]> values = new ArrayList<>();
        long stepSeconds = parseStep(query.getStep());
        long start = query.getStart();
        long end = query.getEnd();

        for (long ts = start; ts <= end; ts += stepSeconds) {
            values.add(new long[]{ts, (long) (Math.random() * 100)});
        }

        return MetricDataVO.builder()
                .metric(query.getMetric())
                .values(values)
                .build();
    }

    private long parseStep(String step) {
        if (step == null || step.isEmpty()) {
            return 60;
        }
        try {
            if (step.endsWith("s")) {
                return Long.parseLong(step.substring(0, step.length() - 1));
            } else if (step.endsWith("m")) {
                return Long.parseLong(step.substring(0, step.length() - 1)) * 60;
            } else if (step.endsWith("h")) {
                return Long.parseLong(step.substring(0, step.length() - 1)) * 3600;
            }
            return Long.parseLong(step);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse step '{}', defaulting to 60s", step);
            return 60;
        }
    }
}
