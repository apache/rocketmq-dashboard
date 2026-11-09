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
package org.apache.rocketmq.studio.common.util;

/**
 * Shared CSV rendering helpers used by export endpoints. Cells are always quoted and
 * values starting with formula characters ({@code = + - @ \t \r \n}) are prefixed with
 * a single quote to prevent spreadsheet formula injection. Apostrophe-prefixed values
 * whose remainder still starts a formula get the same prefix, mirroring the web
 * escaper ({@code escapeCsvCell}) so both exporters behave identically and the web
 * importer's single-apostrophe strip restores the original value.
 */
public final class CsvUtil {

    public static final String CRLF = "\r\n";
    public static final String FORMULA_PREFIX_CHARS = "=+-@\t\r\n";

    private CsvUtil() {
    }

    public static void appendRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(toCell(values[i]));
        }
        csv.append(CRLF);
    }

    public static String toCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (needsFormulaEscape(text)) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /**
     * A cell needs the protection apostrophe when it starts with a formula character, or
     * when it starts with one or more apostrophes followed by a formula character — the
     * web importer strips exactly one leading apostrophe from such cells, so exporting
     * {@code '=<expr>} without a second apostrophe would corrupt the value on the
     * export-to-import round trip.
     */
    private static boolean needsFormulaEscape(String text) {
        if (text.isEmpty()) {
            return false;
        }
        if (FORMULA_PREFIX_CHARS.indexOf(text.charAt(0)) >= 0) {
            return true;
        }
        int index = 0;
        while (index < text.length() && text.charAt(index) == '\'') {
            index++;
        }
        return index > 0 && index < text.length()
                && FORMULA_PREFIX_CHARS.indexOf(text.charAt(index)) >= 0;
    }
}
