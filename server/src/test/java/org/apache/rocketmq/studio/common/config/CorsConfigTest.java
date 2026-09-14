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

package org.apache.rocketmq.studio.common.config;

import org.apache.rocketmq.studio.instance.dlq.DLQController;
import org.apache.rocketmq.studio.instance.dlq.DLQExcelExportResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQExportResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The DLQ export endpoints report scan completeness (truncation, failed queues, export
 * limit) through {@code X-DLQ-Export-*} response headers, and the web UI turns them into
 * its "export may be incomplete" warning. Browsers only let cross-origin JavaScript read
 * response headers that the server declares in {@code Access-Control-Expose-Headers}, so
 * the CORS mapping must list them — otherwise split frontend/backend deployments silently
 * lose the truncation signal.
 */
@WebMvcTest(value = DLQController.class, properties = "studio.cors.allowed-origins=http://localhost:5173")
@AutoConfigureMockMvc(addFilters = false)
@Import(CorsConfig.class)
class CorsConfigTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DLQService dlqService;

    @Test
    void corsMappingShouldDeclareTheDlqExportHeaders() {
        ExposingCorsRegistry registry = new ExposingCorsRegistry();
        new CorsConfig(FRONTEND_ORIGIN).addCorsMappings(registry);

        CorsConfiguration configuration = registry.getCorsConfigurations().get("/**");
        assertNotNull(configuration);
        List<String> exposed = configuration.getExposedHeaders();
        assertNotNull(exposed, "CORS mapping must expose the DLQ export scan headers");
        List<String> lowerCased = exposed.stream().map(String::toLowerCase).toList();
        assertTrue(lowerCased.containsAll(List.of(
                        "x-dlq-export-truncated", "x-dlq-export-failedqueues", "x-dlq-export-limit")),
                () -> "CORS mapping must expose the DLQ export scan headers, but declared: " + exposed);
    }

    @Test
    void excelExportShouldExposeScanHeadersToCrossOriginClients() throws Exception {
        when(dlqService.exportExcel(eq("instance-1"), eq("test-group"), isNull(), isNull(), isNull()))
                .thenReturn(DLQExcelExportResultVO.builder()
                        .data(new byte[0])
                        .truncated(true)
                        .failedQueueCount(2)
                        .limit(5000)
                        .build());

        MvcResult result = mockMvc.perform(get("/api/dlq/export-excel")
                        .param("instanceId", "instance-1")
                        .param("groupName", "test-group")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("X-DLQ-Export-Truncated", "true"))
                .andReturn();

        assertExposeHeadersDeclareDlqExportHeaders(result);
    }

    @Test
    void jsonExportShouldExposeScanHeadersToCrossOriginClients() throws Exception {
        when(dlqService.exportMessages(eq("instance-1"), eq("test-group"), isNull(), isNull(), isNull()))
                .thenReturn(DLQExportResultVO.builder()
                        .messages(List.of())
                        .truncated(true)
                        .failedQueueCount(2)
                        .limit(5000)
                        .build());

        MvcResult result = mockMvc.perform(get("/api/dlq/export")
                        .param("instanceId", "instance-1")
                        .param("groupName", "test-group")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("X-DLQ-Export-Truncated", "true"))
                .andReturn();

        assertExposeHeadersDeclareDlqExportHeaders(result);
    }

    private static void assertExposeHeadersDeclareDlqExportHeaders(MvcResult result) {
        List<?> declared = result.getResponse()
                .getHeaderValues(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        String exposed = declared.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","))
                .toLowerCase();
        assertTrue(exposed.contains("x-dlq-export-truncated")
                        && exposed.contains("x-dlq-export-failedqueues")
                        && exposed.contains("x-dlq-export-limit"),
                () -> "Cross-origin browsers cannot read the DLQ export scan headers; "
                        + "Access-Control-Expose-Headers was: " + declared);
    }

    /** {@link CorsRegistry#getCorsConfigurations} is protected; widen it for assertions. */
    private static final class ExposingCorsRegistry extends CorsRegistry {
        @Override
        public Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }
}
