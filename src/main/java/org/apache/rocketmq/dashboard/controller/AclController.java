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

import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.Entry;
import org.apache.rocketmq.dashboard.model.Policy;
import org.apache.rocketmq.dashboard.model.PolicyRequest;
import org.apache.rocketmq.dashboard.model.request.UserCreateRequest;
import org.apache.rocketmq.dashboard.model.request.UserInfoParam;
import org.apache.rocketmq.dashboard.model.request.UserUpdateRequest;
import org.apache.rocketmq.dashboard.service.AclService;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/acl")
public class AclController {

    @Autowired
    private AclService aclService;

    @GetMapping("/users.query")
    @ResponseBody
    public List<ACLUser> listUsers() {
        return aclService.listUsers();
    }

    @GetMapping("/acls.query")
    @ResponseBody
    public List<ACLPolicy> listAcls(@RequestParam(required = false) String username) {
        return aclService.listPolicies(username);
    }

    @PostMapping("/createAcl.do")
    @ResponseBody
    public Object createAcl(@RequestBody PolicyRequest request) {
        ACLPolicy policy = buildACLPolicy(request);
        return aclService.addPolicy(policy);
    }

    @DeleteMapping("/deleteUser.do")
    @ResponseBody
    public Object deleteUser(@RequestParam String username) {
        return aclService.deleteUser(username);
    }

    @RequestMapping(value = "/updateUser.do", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Object updateUser(@RequestBody UserUpdateRequest request) {
        ACLUser user = buildACLUser(request.getUserInfo());
        return aclService.updateUser(user);
    }

    @PostMapping("/createUser.do")
    @ResponseBody
    public Object createUser(@RequestBody UserCreateRequest request) {
        ACLUser user = buildACLUser(request.getUserInfo());
        return aclService.createUser(user);
    }

    @DeleteMapping("/deleteAcl.do")
    @ResponseBody
    public Object deleteAcl(
            @RequestParam String subject,
            @RequestParam(required = false) String resource) {
        return aclService.removePolicy(subject, resource);
    }

    @RequestMapping(value = "/updateAcl.do", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Object updateAcl(@RequestBody PolicyRequest request) {
        ACLPolicy policy = buildACLPolicy(request);
        return aclService.addPolicy(policy);
    }

    private ACLPolicy buildACLPolicy(PolicyRequest request) {
        ACLPolicy policy = new ACLPolicy();
        Set<String> users = new HashSet<>();
        if (request.getSubject() != null) {
            users.add(request.getSubject());
        }
        policy.setUsers(users);

        Set<String> resources = new HashSet<>();
        Set<String> actions = new HashSet<>();
        if (request.getPolicies() != null) {
            for (Policy p : request.getPolicies()) {
                if (p.getPolicyType() != null) {
                    policy.setPolicyType(p.getPolicyType());
                }
                if (p.getEntries() != null) {
                    for (Entry entry : p.getEntries()) {
                        if (entry.getResource() != null) {
                            resources.addAll(entry.getResource());
                        }
                        if (entry.getActions() != null) {
                            actions.addAll(entry.getActions());
                        }
                    }
                }
            }
        }
        policy.setResources(resources);
        policy.setActions(actions);
        return policy;
    }

    private ACLUser buildACLUser(UserInfoParam userInfo) {
        ACLUser user = new ACLUser();
        if (userInfo != null) {
            user.setUserName(userInfo.getUsername());
            user.setAccessKey(userInfo.getPassword());
            user.setUserType(userInfo.getUserType());
            user.setStatus(userInfo.getUserStatus());
        }
        return user;
    }
}