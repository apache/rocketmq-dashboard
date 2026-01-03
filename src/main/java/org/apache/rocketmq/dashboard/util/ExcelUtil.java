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
package org.apache.rocketmq.dashboard.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;

@Slf4j
public class ExcelUtil {

    public static void writeExcel(HttpServletResponse response, List<? extends Object> data, String fileName,
                                  String sheetName, Class clazz) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data list cannot be null or empty");
        }
        
        WriteCellStyle headWriteCellStyle = new WriteCellStyle();
        WriteFont writeFont = new WriteFont();
        writeFont.setFontHeightInPoints((short) 12);
        writeFont.setFontName("Microsoft YaHei UI");
        headWriteCellStyle.setWriteFont(writeFont);
        headWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);

        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        contentWriteCellStyle.setWriteFont(writeFont);
        contentWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        HorizontalCellStyleStrategy horizontalCellStyleStrategy = new HorizontalCellStyleStrategy(headWriteCellStyle, contentWriteCellStyle);
        
        OutputStream outputStream = getOutputStream(fileName, response);
        try {
            log.info("Writing Excel file: {} rows, fileName={}, sheetName={}", data.size(), fileName, sheetName);
            EasyExcel.write(outputStream, clazz)
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet(sheetName)
                    .registerWriteHandler(horizontalCellStyleStrategy)
                    .doWrite(data);
            outputStream.flush();
            log.info("Excel file written successfully: {} rows written", data.size());
        } catch (Exception e) {
            log.error("Error writing Excel file: {} rows, error: {}", data.size(), e.getMessage(), e);
            throw e;
        } finally {
            // Don't close the output stream - let the container handle it
        }
    }

    private static OutputStream getOutputStream(String fileName, HttpServletResponse response) throws Exception {
        fileName = URLEncoder.encode(fileName, "UTF-8");
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
        return response.getOutputStream();
    }
}
