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
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.AclConfig;
import org.apache.rocketmq.common.PlainAccessConfig;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.model.UserInfo;
import org.apache.rocketmq.dashboard.model.request.AclRequest;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.AclService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/acl")
@Permission
public class AclController {

    @Resource
    private AclService aclService;

    @Resource
    private RMQConfigure configure;

    @GetMapping("/enable.query")
    public Object isEnableAcl() {
        return new JsonResult<>(configure.isACLEnabled());
    }

    @GetMapping("/config.query")
    public AclConfig getAclConfig(HttpServletRequest request) {
        if (!configure.isLoginRequired()) {
            return aclService.getAclConfig(false);
        }
        UserInfo userInfo = (UserInfo) WebUtil.getValueFromSession(request, WebUtil.USER_INFO);
        // if user info is null but reach here, must exclude secret key for safety.
        return aclService.getAclConfig(userInfo == null || userInfo.getUser().getType() != User.ADMIN);
    }

    @PostMapping("/add.do")
    public Object addAclConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(config.getAccessKey()), "accessKey is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(config.getSecretKey()), "secretKey is null");
        aclService.addAclConfig(config);
        return true;
    }

    @PostMapping("/delete.do")
    public Object deleteAclConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(config.getAccessKey()), "accessKey is null");
        aclService.deleteAclConfig(config);
        return true;
    }

    @PostMapping("/update.do")
    public Object updateAclConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(config.getSecretKey()), "secretKey is null");
        aclService.updateAclConfig(config);
        return true;
    }

    @PostMapping("/topic/add.do")
    public Object addAclTopicConfig(@RequestBody AclRequest request) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getConfig().getAccessKey()), "accessKey is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getConfig().getSecretKey()), "secretKey is null");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(request.getConfig().getTopicPerms()), "topic perms is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getTopicPerm()), "topic perm is null");
        aclService.addOrUpdateAclTopicConfig(request);
        return true;
    }

    @PostMapping("/group/add.do")
    public Object addAclGroupConfig(@RequestBody AclRequest request) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getConfig().getAccessKey()), "accessKey is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getConfig().getSecretKey()), "secretKey is null");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(request.getConfig().getGroupPerms()), "group perms is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getGroupPerm()), "group perm is null");
        aclService.addOrUpdateAclGroupConfig(request);
        return true;
    }

    @PostMapping("/perm/delete.do")
    public Object deletePermConfig(@RequestBody AclRequest request) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getConfig().getAccessKey()), "accessKey is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getConfig().getSecretKey()), "secretKey is null");
        aclService.deletePermConfig(request);
        return true;
    }

    @PostMapping("/sync.do")
    public Object syncConfig(@RequestBody PlainAccessConfig config) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(config.getAccessKey()), "accessKey is null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(config.getSecretKey()), "secretKey is null");
        aclService.syncData(config);
        return true;
    }

    @PostMapping("/white/list/add.do")
    public Object addWhiteList(@RequestBody List<String> whiteList) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(whiteList), "white list is null");
        aclService.addWhiteList(whiteList);
        return true;
    }

    @DeleteMapping("/white/list/delete.do")
    public Object deleteWhiteAddr(@RequestParam String request) {
        aclService.deleteWhiteAddr(request);
        return true;
    }

    @PostMapping("/white/list/sync.do")
    public Object synchronizeWhiteList(@RequestBody List<String> whiteList) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(whiteList), "white list is null");
        aclService.synchronizeWhiteList(whiteList);
        return true;
    }
}
