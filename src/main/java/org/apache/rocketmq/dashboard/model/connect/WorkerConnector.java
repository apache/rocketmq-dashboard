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
