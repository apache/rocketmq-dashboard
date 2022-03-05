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

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.AclConfig;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.PlainAccessConfig;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.dashboard.model.request.AclRequest;
import org.apache.rocketmq.dashboard.service.AbstractCommonService;
import org.apache.rocketmq.dashboard.service.AclService;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AclServiceImpl extends AbstractCommonService implements AclService {

    @Override
    public AclConfig getAclConfig(boolean excludeSecretKey) {
        try {
            Optional<String> addr = getMasterSet().stream().findFirst();
            if (addr.isPresent()) {
                if (!excludeSecretKey) {
                    return mqAdminExt.examineBrokerClusterAclConfig(addr.get());
                } else {
                    AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr.get());
                    if (CollectionUtils.isNotEmpty(aclConfig.getPlainAccessConfigs())) {
                        aclConfig.getPlainAccessConfigs().forEach(pac -> pac.setSecretKey(null));
                    }
                    return aclConfig;
                }
            }
        } catch (Exception e) {
            log.error("getAclConfig error.", e);
            throw Throwables.propagate(e);
        }
        AclConfig aclConfig = new AclConfig();
        aclConfig.setGlobalWhiteAddrs(Collections.emptyList());
        aclConfig.setPlainAccessConfigs(Collections.emptyList());
        return aclConfig;
    }

    @Override
    public void addAclConfig(PlainAccessConfig config) {
        try {
            Set<String> masterSet = getMasterSet();

            if (masterSet.isEmpty()) {
                throw new IllegalStateException("broker addr list is empty");
            }
            // check to see if account is exists
            for (String addr : masterSet) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                List<PlainAccessConfig> plainAccessConfigs = aclConfig.getPlainAccessConfigs();
                for (PlainAccessConfig pac : plainAccessConfigs) {
                    if (pac.getAccessKey().equals(config.getAccessKey())) {
                        throw new IllegalArgumentException(String.format("broker: %s, exist accessKey: %s", addr, config.getAccessKey()));
                    }
                }
            }

            // all broker
            for (String addr : getBrokerAddrs()) {
                mqAdminExt.createAndUpdatePlainAccessConfig(addr, config);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }

    @Override
    public void deleteAclConfig(PlainAccessConfig config) {
        try {
            for (String addr : getBrokerAddrs()) {
                log.info("Start to delete acl [{}] from broker [{}]", config.getAccessKey(), addr);
                if (isExistAccessKey(config.getAccessKey(), addr)) {
                    mqAdminExt.deletePlainAccessConfig(addr, config.getAccessKey());
                }
                log.info("Delete acl [{}] from broker [{}] complete", config.getAccessKey(), addr);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void updateAclConfig(PlainAccessConfig config) {
        try {
            for (String addr : getBrokerAddrs()) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                if (aclConfig.getPlainAccessConfigs() != null) {
                    PlainAccessConfig remoteConfig = null;
                    for (PlainAccessConfig pac : aclConfig.getPlainAccessConfigs()) {
                        if (pac.getAccessKey().equals(config.getAccessKey())) {
                            remoteConfig = pac;
                            break;
                        }
                    }
                    if (remoteConfig != null) {
                        remoteConfig.setSecretKey(config.getSecretKey());
                        remoteConfig.setAdmin(config.isAdmin());
                        config = remoteConfig;
                    }
                }
                mqAdminExt.createAndUpdatePlainAccessConfig(addr, config);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void addOrUpdateAclTopicConfig(AclRequest request) {
        try {
            PlainAccessConfig addConfig = request.getConfig();
            for (String addr : getBrokerAddrs()) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                PlainAccessConfig remoteConfig = null;
                if (aclConfig.getPlainAccessConfigs() != null) {
                    for (PlainAccessConfig config : aclConfig.getPlainAccessConfigs()) {
                        if (config.getAccessKey().equals(addConfig.getAccessKey())) {
                            remoteConfig = config;
                            break;
                        }
                    }
                }
                if (remoteConfig == null) {
                    // Maybe the broker no acl config of the access key, therefore add it;
                    mqAdminExt.createAndUpdatePlainAccessConfig(addr, addConfig);
                } else {
                    if (remoteConfig.getTopicPerms() == null) {
                        remoteConfig.setTopicPerms(new ArrayList<>());
                    }
                    removeExist(remoteConfig.getTopicPerms(), request.getTopicPerm().split("=")[0]);
                    remoteConfig.getTopicPerms().add(request.getTopicPerm());
                    mqAdminExt.createAndUpdatePlainAccessConfig(addr, remoteConfig);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void addOrUpdateAclGroupConfig(AclRequest request) {
        try {
            PlainAccessConfig addConfig = request.getConfig();
            for (String addr : getBrokerAddrs()) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                PlainAccessConfig remoteConfig = null;
                if (aclConfig.getPlainAccessConfigs() != null) {
                    for (PlainAccessConfig config : aclConfig.getPlainAccessConfigs()) {
                        if (config.getAccessKey().equals(addConfig.getAccessKey())) {
                            remoteConfig = config;
                            break;
                        }
                    }
                }
                if (remoteConfig == null) {
                    // May be the broker no acl config of the access key, therefore add it;
                    mqAdminExt.createAndUpdatePlainAccessConfig(addr, addConfig);
                } else {
                    if (remoteConfig.getGroupPerms() == null) {
                        remoteConfig.setGroupPerms(new ArrayList<>());
                    }
                    removeExist(remoteConfig.getGroupPerms(), request.getGroupPerm().split("=")[0]);
                    remoteConfig.getGroupPerms().add(request.getGroupPerm());
                    mqAdminExt.createAndUpdatePlainAccessConfig(addr, remoteConfig);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void deletePermConfig(AclRequest request) {
        try {
            PlainAccessConfig deleteConfig = request.getConfig();

            String topic = StringUtils.isNotEmpty(request.getTopicPerm()) ? request.getTopicPerm().split("=")[0] : null;
            String group = StringUtils.isNotEmpty(request.getGroupPerm()) ? request.getGroupPerm().split("=")[0] : null;
            if (deleteConfig.getTopicPerms() != null && topic != null) {
                removeExist(deleteConfig.getTopicPerms(), topic);
            }
            if (deleteConfig.getGroupPerms() != null && group != null) {
                removeExist(deleteConfig.getGroupPerms(), group);
            }

            for (String addr : getBrokerAddrs()) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                PlainAccessConfig remoteConfig = null;
                if (aclConfig.getPlainAccessConfigs() != null) {
                    for (PlainAccessConfig config : aclConfig.getPlainAccessConfigs()) {
                        if (config.getAccessKey().equals(deleteConfig.getAccessKey())) {
                            remoteConfig = config;
                            break;
                        }
                    }
                }
                if (remoteConfig == null) {
                    // Maybe the broker no acl config of the access key, therefore add it;
                    mqAdminExt.createAndUpdatePlainAccessConfig(addr, deleteConfig);
                } else {
                    if (remoteConfig.getTopicPerms() != null && topic != null) {
                        removeExist(remoteConfig.getTopicPerms(), topic);
                    }
                    if (remoteConfig.getGroupPerms() != null && group != null) {
                        removeExist(remoteConfig.getGroupPerms(), group);
                    }
                    mqAdminExt.createAndUpdatePlainAccessConfig(addr, remoteConfig);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }

    @Override
    public void syncData(PlainAccessConfig config) {
        try {
            for (String addr : getBrokerAddrs()) {
                mqAdminExt.createAndUpdatePlainAccessConfig(addr, config);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void addWhiteList(List<String> whiteList) {
        if (whiteList == null) {
            return;
        }
        try {
            for (String addr : getBrokerAddrs()) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                if (aclConfig.getGlobalWhiteAddrs() != null) {
                    aclConfig.setGlobalWhiteAddrs(Stream.of(whiteList, aclConfig.getGlobalWhiteAddrs()).flatMap(Collection::stream).distinct().collect(Collectors.toList()));
                } else {
                    aclConfig.setGlobalWhiteAddrs(whiteList);
                }
                mqAdminExt.updateGlobalWhiteAddrConfig(addr, StringUtils.join(aclConfig.getGlobalWhiteAddrs(), ","));
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void deleteWhiteAddr(String deleteAddr) {
        try {
            for (String addr : getBrokerAddrs()) {
                AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
                if (aclConfig.getGlobalWhiteAddrs() == null || aclConfig.getGlobalWhiteAddrs().isEmpty()) {
                    continue;
                }
                aclConfig.getGlobalWhiteAddrs().remove(deleteAddr);
                mqAdminExt.updateGlobalWhiteAddrConfig(addr, StringUtils.join(aclConfig.getGlobalWhiteAddrs(), ","));
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void synchronizeWhiteList(List<String> whiteList) {
        if (whiteList == null) {
            return;
        }
        try {
            for (String addr : getBrokerAddrs()) {
                mqAdminExt.updateGlobalWhiteAddrConfig(addr, StringUtils.join(whiteList, ","));
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void removeExist(List<String> list, String name) {
        Iterator<String> iterator = list.iterator();
        while (iterator.hasNext()) {
            String v = iterator.next();
            String cmp = v.split("=")[0];
            if (cmp.equals(name)) {
                iterator.remove();
            }
        }
    }

    private boolean isExistAccessKey(String accessKey,
        String addr) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        AclConfig aclConfig = mqAdminExt.examineBrokerClusterAclConfig(addr);
        List<PlainAccessConfig> plainAccessConfigs = aclConfig.getPlainAccessConfigs();
        if (plainAccessConfigs == null || plainAccessConfigs.isEmpty()) {
            return false;
        }
        for (PlainAccessConfig config : plainAccessConfigs) {
            if (accessKey.equals(config.getAccessKey())) {
                return true;
            }
        }
        return false;
    }

    private Set<BrokerData> getBrokerDataSet() throws InterruptedException, RemotingConnectException, RemotingTimeoutException, RemotingSendRequestException, MQBrokerException {
        ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        Map<String, BrokerData> brokerDataMap = clusterInfo.getBrokerAddrTable();
        return new HashSet<>(brokerDataMap.values());
    }

    private Set<String> getMasterSet() throws InterruptedException, MQBrokerException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        return getBrokerDataSet().stream().map(data -> data.getBrokerAddrs().get(MixAll.MASTER_ID)).collect(Collectors.toSet());
    }

    private Set<String> getBrokerAddrs() throws InterruptedException, MQBrokerException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        Set<String> brokerAddrs = new HashSet<>();
        getBrokerDataSet().forEach(data -> brokerAddrs.addAll(data.getBrokerAddrs().values()));
        return brokerAddrs;
    }
}
