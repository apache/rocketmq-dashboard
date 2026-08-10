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
package org.apache.rocketmq.studio.provider.tencent;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * REST endpoints for browsing Tencent Cloud RocketMQ 5.x instances with a stored credential.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cloud/tencent")
public class TencentCatalogController {

    private final TencentCatalogService catalogService;

    @GetMapping("/regions")
    public Result<List<CloudRegionVO>> listRegions(@RequestParam String credentialId) {
        return Result.ok(catalogService.listRegions(credentialId));
    }

    @GetMapping("/instances")
    public Result<List<CloudInstanceOptionVO>> listInstances(@RequestParam String credentialId,
                                                             @RequestParam String regionId,
                                                             @RequestParam(required = false) String search) {
        return Result.ok(catalogService.listCloudInstances(credentialId, regionId, search));
    }
}
