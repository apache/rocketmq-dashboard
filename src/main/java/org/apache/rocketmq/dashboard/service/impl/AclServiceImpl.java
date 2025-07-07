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

package org.apache.rocketmq.dashboard.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.dashboard.model.Entry;
import org.apache.rocketmq.dashboard.model.Policy;
import org.apache.rocketmq.dashboard.model.PolicyRequest;
import org.apache.rocketmq.dashboard.model.UserInfoDto;
import org.apache.rocketmq.dashboard.model.request.UserInfoParam;
import org.apache.rocketmq.dashboard.service.AbstractCommonService;
import org.apache.rocketmq.dashboard.service.AclService;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AclServiceImpl extends AbstractCommonService implements AclService {

    private Logger logger = LoggerFactory.getLogger(AclServiceImpl.class);


    @Autowired
    private MQAdminExt mqAdminExt;

    @Autowired
    private ClusterInfoService clusterInfoService;


    @Override
    public List<UserInfoDto> listUsers(String clusterName, String brokerName) {

        List<String> brokerAddrList = getBrokerAddressList(clusterName, brokerName);
        Set<UserInfoDto> commonUsers = new HashSet<>();
        final boolean[] firstIteration = {true};
        brokerAddrList.forEach(address -> {
            List<UserInfo> userList;
            try {
                userList = mqAdminExt.listUser(address, "");
            } catch (Exception ex) {
                logger.error("Failed to list users from broker: {}", address, ex);
                throw new RuntimeException("Failed to list users", ex);
            }

            List<UserInfoDto> userListDtos = new ArrayList<>();
            userList.forEach(user -> {
                UserInfoDto userInfoDto = new UserInfoDto();
                userListDtos.add(userInfoDto.setUserInfo(user));
            });
            if (!userList.isEmpty()) {
                Set<UserInfoDto> currentUsers = new HashSet<>(userListDtos);
                if (firstIteration[0]) {
                    commonUsers.addAll(userListDtos);
                    firstIteration[0] = false;
                } else {
                    commonUsers.retainAll(currentUsers);
                }
            } else {
                logger.warn("No users found for broker: {}", address);
            }
        });

        return new ArrayList<>(commonUsers);
    }

    @Override
    public Object listAcls(String clusterName, String brokerName, String searchParam) {
        List<String> brokerAddrList = getBrokerAddressList(clusterName, brokerName);
        Set<org.apache.rocketmq.dashboard.model.AclInfo> commonAcls = new HashSet<>();
        final boolean[] firstIteration = {true};
        ObjectMapper mapper = new ObjectMapper(); // Initialize ObjectMapper once

        brokerAddrList.forEach(address -> {
            List<AclInfo> aclListForBroker;
            try {
                String user = searchParam != null ? searchParam : "";
                String res = searchParam != null ? searchParam : "";
                // Combine results from both listAcl calls for a single broker
                List<AclInfo> byUser = mqAdminExt.listAcl(address, user, "");
                List<AclInfo> byRes = mqAdminExt.listAcl(address, "", res);

                aclListForBroker = new ArrayList<>();
                if (byUser != null) {
                    aclListForBroker.addAll(byUser);
                }
                if (byRes != null) {
                    aclListForBroker.addAll(byRes);
                }

                // Deduplicate ACLs for the current broker to ensure accurate intersection
                Set<AclInfo> uniqueAclsForBroker = new HashSet<>();
                Set<String> uniqueAclStringsForBroker = new HashSet<>();
                for (AclInfo acl : aclListForBroker) {
                    try {
                        String aclString = mapper.writeValueAsString(acl);
                        if (uniqueAclStringsForBroker.add(aclString)) {
                            uniqueAclsForBroker.add(acl);
                        }
                    } catch (Exception e) {
                        logger.error("Error serializing AclInfo for broker {}: {}", address, e.getMessage());
                    }
                }
                aclListForBroker = new ArrayList<>(uniqueAclsForBroker);

            } catch (Exception ex) {
                logger.error("Failed to list ACLs from broker: {}", address, ex);
                throw new RuntimeException("Failed to list ACLs", ex);
            }
            List<org.apache.rocketmq.dashboard.model.AclInfo> aclInfoList = new ArrayList<>();
            aclListForBroker.forEach(acl -> {
                org.apache.rocketmq.dashboard.model.AclInfo aclInfo = new org.apache.rocketmq.dashboard.model.AclInfo();
                aclInfo.copyFrom(acl);
                aclInfoList.add(aclInfo);
            });
            if (!aclListForBroker.isEmpty()) {
                Set<org.apache.rocketmq.dashboard.model.AclInfo> currentAcls = new HashSet<>(aclInfoList);
                if (firstIteration[0]) {
                    commonAcls.addAll(currentAcls);
                    firstIteration[0] = false;
                } else {
                    commonAcls.retainAll(currentAcls);
                }
            } else {
                logger.warn("No ACLs found for broker: {}", address);
                if (firstIteration[0]) {
                    firstIteration[0] = false;
                } else {
                    commonAcls.clear(); // If any broker has no ACLs, the common set will be empty
                }
            }
        });
        return new ArrayList<>(commonAcls);
    }

    @Override
    public Object createAcl(PolicyRequest policyRequest) {
        List<String> successfulResources = new ArrayList<>();

        if (policyRequest == null || policyRequest.getPolicies() == null || policyRequest.getPolicies().isEmpty()) {
            logger.warn("Policy request is null or policies list is empty. No ACLs to create.");
            return successfulResources;
        }

        String subject = policyRequest.getSubject();
        if (subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty.");
        }

        // Get the broker address list for creating ACLs on all relevant brokers
        List<String> brokerAddrList = getBrokerAddressList(policyRequest.getClusterName(), policyRequest.getBrokerName());

        for (Policy policy : policyRequest.getPolicies()) {
            if (policy.getEntries() != null && !policy.getEntries().isEmpty()) {
                for (Entry entry : policy.getEntries()) {
                    if (entry.getResource() != null && !entry.getResource().isEmpty()) {
                        for (String resource : entry.getResource()) {
                            AclInfo aclInfo = new AclInfo();
                            List<AclInfo.PolicyInfo> aclPolicies = new ArrayList<>();
                            AclInfo.PolicyInfo policyInfo = new AclInfo.PolicyInfo();
                            List<AclInfo.PolicyEntryInfo> entries = new ArrayList<>();
                            AclInfo.PolicyEntryInfo entryInfo = new AclInfo.PolicyEntryInfo();

                            entryInfo.setActions(entry.getActions());
                            entryInfo.setDecision(entry.getDecision());
                            entryInfo.setResource(resource);
                            entryInfo.setSourceIps(entry.getSourceIps());
                            entries.add(entryInfo);

                            policyInfo.setEntries(entries);
                            policyInfo.setPolicyType(policy.getPolicyType());
                            aclPolicies.add(policyInfo);

                            aclInfo.setPolicies(aclPolicies);
                            aclInfo.setSubject(subject);

                            for (String brokerAddress : brokerAddrList) {
                                try {
                                    logger.info("Attempting to create ACL for subject: {}, resource: {} on broker: {}", subject, resource, brokerAddress);
                                    mqAdminExt.createAcl(brokerAddress, aclInfo);
                                    logger.info("Successfully created ACL for subject: {}, resource: {} on broker: {}", subject, resource, brokerAddress);
                                } catch (Exception ex) {
                                    throw new RuntimeException("Failed to create ACL on broker " + brokerAddress + ex.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void deleteUser(String clusterName, String brokerName, String username) {
        List<String> brokerAddrList = getBrokerAddressList(clusterName, brokerName);

        for (String address : brokerAddrList) {
            try {
                mqAdminExt.deleteUser(address, username);
                logger.info("Successfully deleted user: {} from broker: {}", username, address);
            } catch (Exception ex) {
                logger.error("Failed to delete user: {} from broker: {}", username, address, ex);
                throw new RuntimeException("Failed to delete user on broker " + address + ex.getMessage());
            }
        }
    }

    @Override
    public void updateUser(String clusterName, String brokerName, UserInfoParam userParam) {
        UserInfo user = new UserInfo();
        user.setUsername(userParam.getUsername());
        user.setPassword(userParam.getPassword());
        user.setUserStatus(userParam.getUserStatus());
        user.setUserType(userParam.getUserType());

        List<String> brokerAddrList = getBrokerAddressList(clusterName, brokerName);

        for (String address : brokerAddrList) {
            try {
                mqAdminExt.updateUser(address, user);
                logger.info("Successfully updated user: {} on broker: {}", userParam.getUsername(), address);
            } catch (Exception ex) {
                logger.error("Failed to update user: {} on broker: {}", userParam.getUsername(), address, ex);
                throw new RuntimeException("Failed to update user on broker " + address + ex.getMessage());
            }
        }
    }

    @Override
    public void createUser(String clusterName, String brokerName, UserInfoParam userParam) {
        UserInfo user = new UserInfo();
        user.setUsername(userParam.getUsername());
        user.setPassword(userParam.getPassword());
        user.setUserStatus(userParam.getUserStatus());
        user.setUserType(userParam.getUserType());

        List<String> brokerAddrList = getBrokerAddressList(clusterName, brokerName);

        for (String address : brokerAddrList) {
            try {
                mqAdminExt.createUser(address, user);
                logger.info("Successfully created user: {} on broker: {}", userParam.getUsername(), address);
            } catch (Exception ex) {
                logger.error("Failed to create user: {} on broker: {}", userParam.getUsername(), address, ex);
                throw new RuntimeException("Failed to create user on broker " + address + ex.getMessage());
            }
        }
    }

    @Override
    public void deleteAcl(String clusterName, String brokerName, String subject, String resource) {
        List<String> brokerAddrList = getBrokerAddressList(clusterName, brokerName);
        String res = resource != null ? resource : "";

        for (String address : brokerAddrList) {
            try {
                mqAdminExt.deleteAcl(address, subject, res);
                logger.info("Successfully deleted ACL for subject: {} and resource: {} on broker: {}", subject, resource, address);
            } catch (Exception ex) {
                logger.error("Failed to delete ACL for subject: {} and resource: {} on broker: {}", subject, resource, address, ex);
                throw new RuntimeException("Failed to delete ACL on broker " + address + ex.getMessage());
            }
        }
    }

    @Override
    public void updateAcl(PolicyRequest policyRequest) {
        if (policyRequest == null || policyRequest.getPolicies() == null || policyRequest.getPolicies().isEmpty()) {
            logger.warn("Policy request is null or policies list is empty. No ACLs to update.");
            return;
        }

        String subject = policyRequest.getSubject();
        if (subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty.");
        }

        List<String> brokerAddrList = getBrokerAddressList(policyRequest.getClusterName(), policyRequest.getBrokerName());

        for (Policy policy : policyRequest.getPolicies()) {
            if (policy.getEntries() != null && !policy.getEntries().isEmpty()) {
                for (Entry entry : policy.getEntries()) {
                    if (entry.getResource() != null && !entry.getResource().isEmpty()) {
                        for (String resource : entry.getResource()) {
                            AclInfo aclInfo = new AclInfo();
                            List<AclInfo.PolicyInfo> aclPolicies = new ArrayList<>();
                            AclInfo.PolicyInfo policyInfo = new AclInfo.PolicyInfo();
                            List<AclInfo.PolicyEntryInfo> entries = new ArrayList<>();
                            AclInfo.PolicyEntryInfo entryInfo = new AclInfo.PolicyEntryInfo();

                            entryInfo.setActions(entry.getActions());
                            entryInfo.setDecision(entry.getDecision());
                            entryInfo.setResource(resource);
                            entryInfo.setSourceIps(entry.getSourceIps());
                            entries.add(entryInfo);

                            policyInfo.setEntries(entries);
                            policyInfo.setPolicyType(policy.getPolicyType());
                            aclPolicies.add(policyInfo);

                            aclInfo.setPolicies(aclPolicies);
                            aclInfo.setSubject(subject);

                            for (String brokerAddress : brokerAddrList) {
                                try {
                                    mqAdminExt.updateAcl(brokerAddress, aclInfo);
                                    logger.info("Successfully updated ACL for subject: {}, resource: {} on broker: {}", subject, resource, brokerAddress);
                                } catch (Exception ex) {
                                    logger.error("Failed to update ACL for subject: {}, resource: {} on broker: {}", subject, resource, brokerAddress, ex);
                                    throw new RuntimeException("Failed to update ACL on broker " + brokerAddress + ex.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    public List<String> getBrokerAddressList(String clusterName, String brokerName) {
        ClusterInfo clusterInfo = clusterInfoService.get();
        List<String> brokerAddressList = new ArrayList<>();
        if (brokerName != null) {
            for (String brokerNameKey : changeToBrokerNameSet(clusterInfo.getClusterAddrTable(),
                    new ArrayList<>(), List.of(brokerName))) {
                clusterInfo.getBrokerAddrTable()
                        .get(brokerNameKey)
                        .getBrokerAddrs()
                        .forEach((Long key, String value) -> brokerAddressList.add(value));
            }
        } else {
            if (clusterName == null || clusterName.isEmpty()) {
                logger.warn("Cluster name is null or empty. Cannot retrieve broker addresses.");
                throw new IllegalArgumentException("Cluster name cannot be null or empty.");
            }
            if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null || clusterInfo.getBrokerAddrTable().isEmpty()) {
                logger.warn("Cluster information is not available or has no broker addresses.");
                throw new RuntimeException("Cluster information is not available or has no broker addresses.");
            }
            for (String brokerNameKey : changeToBrokerNameSet(clusterInfo.getClusterAddrTable(),
                    List.of(clusterName), new ArrayList<>())) {
                clusterInfo.getBrokerAddrTable()
                        .get(brokerNameKey)
                        .getBrokerAddrs()
                        .forEach((Long key, String value) -> brokerAddressList.add(value));
            }
        }
        return brokerAddressList;
    }

}
