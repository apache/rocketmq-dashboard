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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilTest {

    @Test
    void appendRowShouldQuoteAndTerminateWithCrlfTest() {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, "Name", null, 7);
        assertThat(csv.toString()).isEqualTo("\"Name\",\"\",\"7\"\r\n");
    }

    @Test
    void toCellShouldEscapeQuotesAndFormulaPrefixesTest() {
        assertThat(CsvUtil.toCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvUtil.toCell("=SUM(A1)")).isEqualTo("\"'=SUM(A1)\"");
        assertThat(CsvUtil.toCell("+cmd")).isEqualTo("\"'+cmd\"");
    }

    @Test
    void toCellShouldEscapeApostrophePrefixedFormulasTest() {
        assertThat(CsvUtil.toCell("'=HYPERLINK(\"a\",\"b\")"))
                .isEqualTo("\"''=HYPERLINK(\"\"a\"\",\"\"b\"\")\"");
        assertThat(CsvUtil.toCell("''=SUM(A1)")).isEqualTo("\"'''=SUM(A1)\"");
        assertThat(CsvUtil.toCell("'-1")).isEqualTo("\"''-1\"");
    }

    @Test
    void toCellShouldLeavePlainApostrophesAloneTest() {
        assertThat(CsvUtil.toCell("it's fine")).isEqualTo("\"it's fine\"");
        assertThat(CsvUtil.toCell("'quoted'")).isEqualTo("\"'quoted'\"");
        assertThat(CsvUtil.toCell("'")).isEqualTo("\"'\"");
    }

    @Test
    void escapedCellsSurviveTheWebImportersSingleApostropheStripTest() {
        // The web importer (resourceCsvImport.ts) removes exactly one protection
        // apostrophe from cells whose remainder still starts a formula; the escaped
        // cell must keep the original value recoverable through that strip.
        String[] originals = {"=SUM(A1)", "'=SUM(A1)", "''=SUM(A1)", "'-tag", "+x"};
        for (String original : originals) {
            String cell = CsvUtil.toCell(original);
            String unquoted = cell.substring(1, cell.length() - 1).replace("\"\"", "\"");
            String stripped = unquoted.replaceFirst("^'(?='*[=+\\-@\t\r\n])", "");
            assertThat(stripped).isEqualTo(original);
        }
    }
}
