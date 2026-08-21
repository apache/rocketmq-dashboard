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
package org.apache.rocketmq.studio.settings;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.domain.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/general")
    public Result<GeneralSettingsVO> getGeneralSettings() {
        return Result.ok(settingsService.getGeneralSettings());
    }

    @PostMapping("/general/save")
    public Result<Void> saveGeneralSettings(@Valid @RequestBody GeneralSettingsUpdateDTO request) {
        settingsService.saveGeneralSettings(request.toSettings());
        return Result.ok();
    }

    @GetMapping("/datasources")
    public Result<List<DataSourceVO>> listDataSources() {
        return Result.ok(settingsService.listDataSources());
    }

    @GetMapping("/datasources/page")
    public Result<PageResult<DataSourceVO>> listDataSources(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(settingsService.listDataSources(search, type, page, pageSize));
    }

    @PostMapping("/datasources/create")
    public Result<DataSourceVO> createDataSource(@Valid @RequestBody DataSourceDTO request) {
        return Result.ok(settingsService.createDataSource(request.toDataSourceVO()));
    }

    @PostMapping("/datasources/update")
    public Result<DataSourceVO> updateDataSource(@Valid @RequestBody DataSourceDTO request) {
        return Result.ok(settingsService.updateDataSource(request.toDataSourceVO()));
    }

    @PostMapping("/datasources/delete")
    public Result<Void> deleteDataSource(@RequestParam(required = false) String key) {
        settingsService.deleteDataSource(key);
        return Result.ok();
    }

    @PostMapping("/datasources/test")
    public Result<DataSourceTestResultVO> testDataSource(@Valid @RequestBody DataSourceTestDTO request) {
        return Result.ok(settingsService.testDataSource(request));
    }

}
