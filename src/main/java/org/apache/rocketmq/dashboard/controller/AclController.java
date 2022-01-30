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
package org.apache.rocketmq.dashboard.controller;

import com.google.common.base.Preconditions;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.AclConfig;
import org.apache.rocketmq.common.PlainAccessConfig;
import org.apache.rocketmq.dashboard.model.request.AclRequest;
import org.apache.rocketmq.dashboard.service.AclService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/acl")
public class AclController {

    @Autowired
    private AclService aclService;

    @Value("${rocketmq.config.accessKey}")
    private String accessKey;

    @Value("${rocketmq.config.secretKey}")
    private String secretKey;

    @GetMapping("/enable.query")
    public Object isEnableAcl() {
        boolean isEnable = StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey);
        return new JsonResult<>(isEnable);
    }

    @GetMapping("/config.query")
    public AclConfig getAclConfig() {
        return aclService.getAclConfig();
    }

    @PostMapping("/add.do")
    public Object addAclConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(config.getAccessKey() != null && !config.getAccessKey().isEmpty(), "accessKey is null");
        Preconditions.checkArgument(config.getSecretKey() != null && !config.getSecretKey().isEmpty(), "secretKey is null");
        aclService.addAclConfig(config);
        return new JsonResult(0, "");
    }

    @PostMapping("/delete.do")
    public Object deleteAclConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(config.getAccessKey() != null && !config.getAccessKey().isEmpty(), "accessKey is null");
        aclService.deleteAclConfig(config);
        return new JsonResult(0, "");
    }

    @PostMapping("/update.do")
    public Object updateAclConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(config.getSecretKey() != null && !config.getSecretKey().isEmpty(), "secretKey is null");
        aclService.updateAclConfig(config);
        return new JsonResult(0, "");
    }

    @PostMapping("/topic/add.do")
    public Object addAclTopicConfig(@RequestBody AclRequest request) {
        Preconditions.checkArgument(request.getConfig().getAccessKey() != null && !request.getConfig().getAccessKey().isEmpty(), "accessKey is null");
        Preconditions.checkArgument(request.getConfig().getSecretKey() != null && !request.getConfig().getSecretKey().isEmpty(), "secretKey is null");
        Preconditions.checkArgument(request.getConfig().getTopicPerms() != null && !request.getConfig().getTopicPerms().isEmpty(), "topic perms is null");
        Preconditions.checkArgument(request.getTopicPerm() != null && !request.getTopicPerm().isEmpty(), "topic perm is null");
        aclService.addOrUpdateAclTopicConfig(request);
        return new JsonResult(0, "");
    }

    @PostMapping("/group/add.do")
    public Object addAclGroupConfig(@RequestBody AclRequest request) {
        Preconditions.checkArgument(request.getConfig().getAccessKey() != null && !request.getConfig().getAccessKey().isEmpty(), "accessKey is null");
        Preconditions.checkArgument(request.getConfig().getSecretKey() != null && !request.getConfig().getSecretKey().isEmpty(), "secretKey is null");
        Preconditions.checkArgument(request.getConfig().getGroupPerms() != null && !request.getConfig().getGroupPerms().isEmpty(), "group perms is null");
        Preconditions.checkArgument(request.getGroupPerm() != null && !request.getGroupPerm().isEmpty(), "group perm is null");
        aclService.addOrUpdateAclGroupConfig(request);
        return new JsonResult(0, "");
    }

    @PostMapping("/perm/delete.do")
    public Object deletePermConfig(@RequestBody AclRequest request) {
        Preconditions.checkArgument(request.getConfig().getAccessKey() != null && !request.getConfig().getAccessKey().isEmpty(), "accessKey is null");
        Preconditions.checkArgument(request.getConfig().getSecretKey() != null && !request.getConfig().getSecretKey().isEmpty(), "secretKey is null");
        aclService.deletePermConfig(request);
        return new JsonResult(0, "");
    }

    @PostMapping("/sync.do")
    public Object syncConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(config.getAccessKey() != null && !config.getAccessKey().isEmpty(), "accessKey is null");
        Preconditions.checkArgument(config.getSecretKey() != null && !config.getSecretKey().isEmpty(), "secretKey is null");
        aclService.syncData(config);
        return new JsonResult(0, "");
    }

    @PostMapping("/white/list/add.do")
    public Object addWhiteList(@RequestBody List<String> whiteList) {
        Preconditions.checkArgument(whiteList != null && !whiteList.isEmpty(), "white list is null");
        aclService.addWhiteList(whiteList);
        return new JsonResult(0, "");
    }

    @DeleteMapping("/white/list/delete.do")
    public Object deleteWhiteAddr(@RequestParam String request) {
        aclService.deleteWhiteAddr(request);
        return new JsonResult(0, "");
    }

    @PostMapping("/white/list/sync.do")
    public Object synchronizeWhiteList(@RequestBody List<String> whiteList) {
        Preconditions.checkArgument(whiteList != null && !whiteList.isEmpty(), "white list is null");
        aclService.synchronizeWhiteList(whiteList);
        return new JsonResult(0, "");
    }
}