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

import org.apache.rocketmq.dashboard.model.PolicyRequest;
import org.apache.rocketmq.dashboard.model.request.UserCreateRequest;
import org.apache.rocketmq.dashboard.model.request.UserUpdateRequest;
import org.apache.rocketmq.dashboard.service.impl.AclServiceImpl;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/acl")
public class AclController {

    @Autowired
    private AclServiceImpl aclService;

    @GetMapping("/users.query")
    @ResponseBody
    public List<UserInfo> listUsers(@RequestParam(required = false) String brokerAddress) {
        return aclService.listUsers(brokerAddress);
    }

    @GetMapping("/acls.query")
    @ResponseBody
    public Object listAcls(
            @RequestParam(required = false) String brokerAddress,
            @RequestParam(required = false) String searchParam) {
        return aclService.listAcls(brokerAddress, searchParam);
    }

    @PostMapping("/createAcl.do")
    @ResponseBody
    public Object createAcl(@RequestBody PolicyRequest request) {
        aclService.createAcl(request);
        return true;
    }

    @DeleteMapping("/deleteUser.do")
    @ResponseBody
    public Object deleteUser(@RequestParam(required = false) String brokerAddress, @RequestParam String username) {
        aclService.deleteUser(brokerAddress, username);
        return true;
    }

    @RequestMapping(value = "/updateUser.do", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Object updateUser(@RequestBody UserUpdateRequest request) {
        aclService.updateUser(request.getBrokerAddress(), request.getUserInfo());
        return true;
    }

    @PostMapping("/createUser.do")
    public Object createUser(@RequestBody UserCreateRequest request) {
        aclService.createUser(request.getBrokerAddress(), request.getUserInfo());
        return true;
    }

    @DeleteMapping("/deleteAcl.do")
    public Object deleteAcl(
            @RequestParam(required = false) String brokerAddress,
            @RequestParam String subject,
            @RequestParam(required = false) String resource) {
        aclService.deleteAcl(brokerAddress, subject, resource);
        return true;
    }

    @RequestMapping(value = "/updateAcl.do", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Object updateAcl(@RequestBody PolicyRequest request) {
        aclService.updateAcl(request);
        return true;
    }


}
