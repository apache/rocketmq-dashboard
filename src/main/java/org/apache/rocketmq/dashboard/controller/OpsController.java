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
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.OpsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/ops")
@Permission
public class OpsController {

    @Resource
    private OpsService opsService;

    @GetMapping("/homePage.query")
    @ResponseBody
    public Object homePage() {
        return opsService.homePageInfo();
    }

    @PostMapping("/updateNameSvrAddr.do")
    @ResponseBody
    public Object updateNameSvrAddr(@RequestParam String nameSvrAddrList) {
        opsService.updateNameSvrAddrList(nameSvrAddrList);
        return true;
    }

    @PostMapping("/addNameSvrAddr.do")
    @ResponseBody
    public Object addNameSvrAddr(@RequestParam String newNamesrvAddr) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(newNamesrvAddr),
            "namesrvAddr can not be blank");
        opsService.addNameSvrAddr(newNamesrvAddr);
        return true;
    }

    @PostMapping("/updateIsVIPChannel.do")
    @ResponseBody
    public Object updateIsVIPChannel(@RequestParam String useVIPChannel) {
        opsService.updateIsVIPChannel(useVIPChannel);
        return true;
    }

    @GetMapping("/rocketMqStatus.query")
    @ResponseBody
    public Object clusterStatus() {
        return opsService.rocketMqStatusCheck();
    }

    @PostMapping("/updateUseTLS.do")
    @ResponseBody
    public Object updateUseTLS(@RequestParam String useTLS) {
        opsService.updateUseTLS(Boolean.parseBoolean(useTLS));
        return true;
    }
}
