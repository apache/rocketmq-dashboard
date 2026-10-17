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

package org.apache.rocketmq.studio.instance.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link DLQExcelExportResultVO}, the Excel DLQ export: the serialized .xlsx bytes plus
 * the same scan-completeness metadata as the JSON export, so the caller can tell whether the
 * sheet covers a full snapshot or a bounded/partial one. The download button warns on
 * truncation, the byte payload must travel intact (Jackson carries it as Base64), and all
 * construction paths must agree.
 */
class DLQExcelExportResultVOTest {

    @Test
    void anExportCarriesTheBytesAndTheScanCompletenessSignals() {
        byte[] sheet = new byte[] {0x50, 0x4b, 0x03, 0x04, 1, 2, 3};
        DLQExcelExportResultVO export = DLQExcelExportResultVO.builder()
                .data(sheet)
                .truncated(true)
                .failedQueueCount(2)
                .limit(5000)
                .build();

        assertThat(export.getData()).isEqualTo(sheet);
        assertThat(export.isTruncated()).isTrue();
        assertThat(export.getFailedQueueCount()).isEqualTo(2);
        assertThat(export.getLimit()).isEqualTo(5000);
    }

    @Test
    void theBytesSurviveAJsonRoundTrip() throws Exception {
        byte[] sheet = new byte[] {0x50, 0x4b, 0x03, 0x04, 1, 2, 3};
        DLQExcelExportResultVO export = DLQExcelExportResultVO.builder()
                .data(sheet)
                .truncated(true)
                .failedQueueCount(2)
                .limit(5000)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(export);
        DLQExcelExportResultVO back = mapper.readValue(json, DLQExcelExportResultVO.class);

        assertThat(back.getData()).isEqualTo(sheet);
        assertThat(back.isTruncated()).isTrue();
        assertThat(back.getFailedQueueCount()).isEqualTo(2);
        assertThat(back.getLimit()).isEqualTo(5000);
        assertThat(back).isEqualTo(export);
    }

    @Test
    void allConstructionPathsAgree() {
        byte[] sheet = new byte[] {1, 2, 3};
        DLQExcelExportResultVO allArgs = new DLQExcelExportResultVO(sheet, false, 0, 5000);
        DLQExcelExportResultVO built = DLQExcelExportResultVO.builder()
                .data(sheet)
                .truncated(false)
                .failedQueueCount(0)
                .limit(5000)
                .build();
        DLQExcelExportResultVO defaulted = new DLQExcelExportResultVO();
        defaulted.setData(sheet);
        defaulted.setTruncated(false);
        defaulted.setFailedQueueCount(0);
        defaulted.setLimit(5000);

        assertThat(allArgs).isEqualTo(built).isEqualTo(defaulted);

        DLQExcelExportResultVO empty = new DLQExcelExportResultVO();
        assertThat(empty.getData()).isNull();
        assertThat(empty.isTruncated()).isFalse();
        assertThat(empty.getFailedQueueCount()).isZero();
        assertThat(empty.getLimit()).isZero();
    }
}
