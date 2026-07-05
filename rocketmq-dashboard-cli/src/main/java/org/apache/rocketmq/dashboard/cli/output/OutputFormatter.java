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
package org.apache.rocketmq.dashboard.cli.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OutputFormatter {

    public enum Format { TABLE, JSON, YAML }

    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final YAMLMapper yamlMapper = new YAMLMapper();

    public static String format(Object data, Format format) {
        switch (format) {
            case JSON:
                return toJson(data);
            case YAML:
                return toYaml(data);
            case TABLE:
            default:
                return toTable(data);
        }
    }

    public static String formatError(ErrorModel error, Format format) {
        switch (format) {
            case JSON:
            case YAML:
                return format(error, format);
            default:
                return "ERROR [" + error.getCode() + "]: " + error.getMessage()
                        + "\nHINT: " + error.getHint();
        }
    }

    private static String toJson(Object data) {
        try {
            return jsonMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String toYaml(Object data) {
        try {
            return yamlMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static String toTable(Object data) {
        if (data == null) {
            return "";
        }

        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty()) {
                return "(empty)";
            }
            Object first = list.get(0);
            if (first instanceof Map) {
                return toAlignedTable((List<Map<String, Object>>) list);
            } else {
                return toNumberedList(list);
            }
        } else if (data instanceof Map) {
            return toVerticalKeyValue((Map<String, Object>) data);
        } else {
            return String.valueOf(data);
        }
    }

    private static String toAlignedTable(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "(empty)";
        }

        // Collect all column names preserving order from first row
        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        // Calculate column widths
        Map<String, Integer> colWidths = new LinkedHashMap<>();
        for (String col : columns) {
            int maxWidth = col.length();
            for (Map<String, Object> row : rows) {
                Object value = row.get(col);
                String strValue = value == null ? "" : String.valueOf(value);
                if (strValue.length() > maxWidth) {
                    maxWidth = strValue.length();
                }
            }
            colWidths.put(col, maxWidth);
        }

        StringBuilder sb = new StringBuilder();

        // Build header separator line
        StringBuilder separator = new StringBuilder();
        for (String col : columns) {
            if (separator.length() > 0) {
                separator.append("-+-");
            }
            int width = colWidths.get(col);
            for (int i = 0; i < width; i++) {
                separator.append('-');
            }
        }

        // Header row
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String col = columns.get(i);
            sb.append(padRight(col, colWidths.get(col)));
        }
        sb.append('\n');
        sb.append(separator);
        sb.append('\n');

        // Data rows
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                String col = columns.get(i);
                Object value = row.get(col);
                String strValue = value == null ? "" : String.valueOf(value);
                sb.append(padRight(strValue, colWidths.get(col)));
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static String toNumberedList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(String.format("%3d. %s%n", i + 1, list.get(i)));
        }
        return sb.toString();
    }

    private static String toVerticalKeyValue(Map<String, Object> map) {
        if (map.isEmpty()) {
            return "(empty)";
        }

        // Find max key length for alignment
        int maxKeyLen = 0;
        for (String key : map.keySet()) {
            if (key.length() > maxKeyLen) {
                maxKeyLen = key.length();
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(padRight(entry.getKey(), maxKeyLen + 2))
                    .append(String.valueOf(entry.getValue()))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String padRight(String s, int length) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= length) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
