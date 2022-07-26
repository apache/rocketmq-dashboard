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
package org.apache.rocketmq.dashboard.model.connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WorkerInfo {
    private static final Logger log = LoggerFactory.getLogger(WorkerInfo.class);

    private String ipAddr;

    private String namesrvAddr;

    private List<WorkerConnector> allocatedConnectors;

    private List<WorkerTask> allocatedTasks;

    public WorkerInfo(String ipAddr, String namesrvAddr, List<WorkerConnector> allocatedConnectors, List<WorkerTask> allocatedTasks) {
        this.ipAddr = ipAddr;
        this.namesrvAddr = namesrvAddr;
        this.allocatedConnectors = allocatedConnectors;
        this.allocatedTasks = allocatedTasks;
    }

    public WorkerInfo() {
    }

    public static Logger getLog() {
        return log;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public List<WorkerConnector> getAllocatedConnectors() {
        return allocatedConnectors;
    }

    public void setAllocatedConnectors(List<WorkerConnector> allocatedConnectors) {
        this.allocatedConnectors = allocatedConnectors;
    }

    public List<WorkerTask> getAllocatedTasks() {
        return allocatedTasks;
    }

    public void setAllocatedTasks(List<WorkerTask> allocatedTasks) {
        this.allocatedTasks = allocatedTasks;
    }

    @Override
    public String toString() {
        return "WorkerInfo{" +
                "ipAddr='" + ipAddr + '\'' +
                ", namesrvAddr='" + namesrvAddr + '\'' +
                ", workingConnectors=" + allocatedConnectors +
                ", existingTasks=" + allocatedTasks +
                '}';
    }
}
