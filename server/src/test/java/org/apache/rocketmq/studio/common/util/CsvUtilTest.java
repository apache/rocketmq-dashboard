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
    void prefixesEveryFormulaInjectionCharacterTest() {
        for (char prefix : new char[] {'=', '+', '-', '@', '\t', '\r', '\n'}) {
            String cell = CsvUtil.toCell(prefix + "payload");
            assertThat(cell).as("prefix char %s", prefix).isEqualTo("\"'" + prefix + "payload\"");
        }
    }

    @Test
    void leavesSafeValuesUnprefixedTest() {
        assertThat(CsvUtil.toCell("123")).isEqualTo("\"123\"");
        assertThat(CsvUtil.toCell("hello")).isEqualTo("\"hello\"");
        assertThat(CsvUtil.toCell("'=already")).isEqualTo("\"'=already\"");
        assertThat(CsvUtil.toCell("")).isEqualTo("\"\"");
        assertThat(CsvUtil.toCell(null)).isEqualTo("\"\"");
    }

    @Test
    void escapesEveryEmbeddedQuoteTest() {
        assertThat(CsvUtil.toCell("say \"hi\" then \"bye\"")).isEqualTo("\"say \"\"hi\"\" then \"\"bye\"\"\"");
    }

    @Test
    void keepsCommasInsideQuotedCellsTest() {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, "a,b", "x");
        assertThat(csv.toString()).isEqualTo("\"a,b\",\"x\"\r\n");
        assertThat(CsvUtil.toCell("a,b")).isEqualTo("\"a,b\"");
    }
}
