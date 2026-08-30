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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.cluster.metrics.BusinessMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.ClusterMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Executes a native rule once without persisting a snapshot, state, or event. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NativeAlertRuleTestService {
    private final InstanceRepository instanceRepository;
    private final List<ClusterMetricsCollector> clusterCollectors;
    private final List<BusinessMetricsCollector> businessCollectors;
    private final AlertRuleEvaluator evaluator;

    public AlertRuleTestResultVO test(AlertRuleVO rule) {
        NativeAlertRulePolicy.validate(rule);
        InstanceVO instance = instanceRepository.findByIdentifier(rule.getInstanceId())
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + rule.getInstanceId()));
        List<MetricSample> samples = new ArrayList<>();
        if (rule.getDomain() == AlertDomain.CLUSTER) {
            for (ClusterMetricsCollector collector : clusterCollectors) {
                try {
                    if (collector.supports(instance)) {
                        samples.addAll(collector.collect(instance));
                    }
                } catch (RuntimeException error) {
                    log.warn("Native cluster metric test collector failed for instance {}: {}",
                            instance.getName(), error.getMessage());
                }
            }
        } else {
            for (BusinessMetricsCollector collector : businessCollectors) {
                try {
                    if (collector.supports(instance)) {
                        samples.addAll(collector.collect(instance));
                    }
                } catch (RuntimeException error) {
                    log.warn("Native business metric test collector failed for instance {}: {}",
                            instance.getName(), error.getMessage());
                }
            }
        }
        return AlertRuleTestResultVO.builder().samples(samples.stream()
                .filter(sample -> rule.getMetric().equals(sample.metricKey()))
                .filter(sample -> NativeAlertRuleScopeMatcher.matches(rule, sample))
                .map(sample -> {
                    AlertEvaluationResult evaluation = evaluator.evaluate(rule, sample);
                    return AlertRuleTestResultVO.Sample.builder().labels(sample.labels())
                            .availability(sample.availability().name()).currentValue(evaluation.currentValue())
                            .conditionMet(evaluation.conditionMet())
                            .unavailableReason(sample.unavailableReason()).build();
                }).toList()).build();
    }

}
