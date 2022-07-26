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

import java.util.Map;

public class WorkerConnector {

    private String connectorName;

    private String connectTopicname;

    private String connectorClass;

    private String namesrvAddr;

    private String workerPort;

    private Long updateTimestamp;

    private Map<String, String> properties;


    public WorkerConnector() {
    }

    public WorkerConnector(String connectorName, String connectTopicname, String connectorClass, String namesrvAddr, String workerPort, Long updateTimestamp, Map<String, String> properties) {
        this.connectorName = connectorName;
        this.connectTopicname = connectTopicname;
        this.connectorClass = connectorClass;
        this.namesrvAddr = namesrvAddr;
        this.workerPort = workerPort;
        this.updateTimestamp = updateTimestamp;
        this.properties = properties;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public String getConnectTopicname() {
        return connectTopicname;
    }

    public void setConnectTopicname(String connectTopicname) {
        this.connectTopicname = connectTopicname;
    }

    public String getConnectorClass() {
        return connectorClass;
    }

    public void setConnectorClass(String connectorClass) {
        this.connectorClass = connectorClass;
    }

    public Long getUpdateTimestamp() {
        return updateTimestamp;
    }

    public void setUpdateTimestamp(Long updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public String getWorkerPort() {
        return workerPort;
    }

    public void setWorkerPort(String workerPort) {
        this.workerPort = workerPort;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void set(String key, String value) {
        properties.put(key, value);
    }


    @Override
    public String toString() {
        return "WorkerConnector{" +
                "connectorName='" + connectorName + '\'' +
                ", connectTopicname='" + connectTopicname + '\'' +
                ", connectorClass='" + connectorClass + '\'' +
                ", updateTimestamp=" + updateTimestamp +
                ", properties=" + properties +
                '}';
    }
}
