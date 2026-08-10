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
package org.apache.rocketmq.studio.cluster.client;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class ProducerConnectionSummaryVO {
    public static final String READY = "READY";
    public static final String WARNING = "WARNING";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String NO_CONNECTIONS = "NO_CONNECTIONS";
    public static final String DUPLICATE_CLIENT_ID = "DUPLICATE_CLIENT_ID";
    public static final String MIXED_CLIENT_VERSION = "MIXED_CLIENT_VERSION";
    public static final String INCOMPLETE_CLIENT_METADATA = "INCOMPLETE_CLIENT_METADATA";

    private int totalConnections;
    private int uniqueClientCount;
    private int uniqueAddressCount;
    private int uniqueLanguageCount;
    private int uniqueVersionCount;
    private List<ProducerConnectionSummaryItemVO> languages = List.of();
    private List<ProducerConnectionSummaryItemVO> versions = List.of();
    private List<String> duplicateClientIds = List.of();
    private List<String> warnings = List.of();
    private String readiness = READY;

    public static ProducerConnectionSummaryVO from(List<ProducerConnectionVO> connections) {
        List<ProducerConnectionVO> safeConnections = connections == null ? List.of() : connections;
        ProducerConnectionSummaryVO summary = new ProducerConnectionSummaryVO();
        summary.totalConnections = safeConnections.size();
        summary.uniqueClientCount = countDistinct(safeConnections, ProducerConnectionVO::getClientId);
        summary.uniqueAddressCount = countDistinct(safeConnections, ProducerConnectionVO::getClientAddr);
        summary.languages = distribution(safeConnections, ProducerConnectionVO::getLanguage);
        summary.versions = distribution(safeConnections, ProducerConnectionVO::getVersionDesc);
        summary.uniqueLanguageCount = summary.languages.size();
        summary.uniqueVersionCount = summary.versions.size();
        summary.duplicateClientIds = duplicateClientIds(safeConnections);
        summary.warnings = warnings(summary, safeConnections);
        summary.readiness = readiness(summary);
        return summary;
    }

    private static int countDistinct(
            List<ProducerConnectionVO> connections, Function<ProducerConnectionVO, String> extractor) {
        return (int) connections.stream()
                .map(extractor)
                .filter(ProducerConnectionSummaryVO::hasText)
                .distinct()
                .count();
    }

    private static List<ProducerConnectionSummaryItemVO> distribution(
            List<ProducerConnectionVO> connections, Function<ProducerConnectionVO, String> extractor) {
        return connections.stream()
                .map(extractor)
                .map(ProducerConnectionSummaryVO::normalizeDimension)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Comparator
                        .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new ProducerConnectionSummaryItemVO(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<String> duplicateClientIds(List<ProducerConnectionVO> connections) {
        return connections.stream()
                .map(ProducerConnectionVO::getClientId)
                .filter(ProducerConnectionSummaryVO::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static List<String> warnings(
            ProducerConnectionSummaryVO summary, List<ProducerConnectionVO> connections) {
        if (summary.totalConnections == 0) {
            return List.of(NO_CONNECTIONS);
        }
        List<String> warnings = new java.util.ArrayList<>();
        if (!summary.duplicateClientIds.isEmpty()) {
            warnings.add(DUPLICATE_CLIENT_ID);
        }
        if (summary.uniqueVersionCount > 1) {
            warnings.add(MIXED_CLIENT_VERSION);
        }
        if (connections.stream().anyMatch(ProducerConnectionSummaryVO::hasIncompleteMetadata)) {
            warnings.add(INCOMPLETE_CLIENT_METADATA);
        }
        return List.copyOf(warnings);
    }

    private static String readiness(ProducerConnectionSummaryVO summary) {
        if (summary.totalConnections == 0) {
            return UNAVAILABLE;
        }
        return summary.warnings.isEmpty() ? READY : WARNING;
    }

    private static boolean hasIncompleteMetadata(ProducerConnectionVO connection) {
        return !hasText(connection.getClientId())
                || !hasText(connection.getClientAddr())
                || !hasText(connection.getLanguage())
                || !hasText(connection.getVersionDesc());
    }

    private static String normalizeDimension(String value) {
        return hasText(value) ? value.trim() : "UNKNOWN";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !Objects.equals(value.trim(), "null");
    }
}
