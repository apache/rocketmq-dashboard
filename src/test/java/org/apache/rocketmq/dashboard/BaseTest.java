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

package org.apache.rocketmq.dashboard;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.mockito.internal.util.MockUtil;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseTest {
    /**
     * Inject the corresponding mock class automatically
     */
    protected void autoInjection() {
        Object container = this;
        final List<Field> localFields = getAllFields(container.getClass());
        for (Field field : localFields) {
            Object destObj = ReflectionUtils.getField(field, container);
            if (MockUtil.isSpy(destObj)) {
                processInjection(destObj, container, localFields);
            }
        }
    }

    protected void processInjection(Object target, final Object localDest, final List<Field> localFields) {
        final List<Field> fields = getAllFields(target.getClass());
        for (Field localField : localFields) {
            for (Field field : fields) {
                if (field.getName()
                        .equals(localField.getName())) {
                    Object obj = ReflectionUtils.getField(field, target);
                    if (obj == null) {
                        Object destObj = ReflectionUtils.getField(localField, localDest);
                        if (MockUtil.isSpy(destObj)) {
                            // Recursive processing
                            processInjection(destObj, localDest, localFields);
                        }
                        // injection
                        ReflectionUtils.setField(field, target, ReflectionUtils.getField(localField, localDest));
                    }
                    break;
                }
            }
        }
    }

    protected List<Field> getAllFields(Class<?> leafClass) {
        final List<Field> fields = new ArrayList<>(32);
        ReflectionUtils.FieldCallback fc = (field) -> {
            ReflectionUtils.makeAccessible(field);
            fields.add(field);
        };
        ReflectionUtils.doWithFields(leafClass, fc);
        return fields;
    }

    protected ClusterInfo getClusterInfo() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", new HashSet<>(Arrays.asList("broker-a")));
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-a");
        HashMap<Long, String> brokerNameTable = new HashMap<>();
        brokerNameTable.put(0L, "localhost:10911");
        brokerData.setBrokerAddrs(brokerNameTable);
        brokerAddrTable.put("broker-a", brokerData);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        return clusterInfo;
    }
}
