package org.apache.rocketmq.dashboard.model.connect;

import java.util.Map;

public class WorkerTask {

    private String connectorName;

    private String connectTopicname;

    private String taskClass;

    private String connectorClass;

    private Long updateTimestamp;

    private Map<String, String> properties;

    public WorkerTask() {
    }

    public WorkerTask(String connectorName, String connectTopicName, String taskClass, String connectorClass, Long updateTimestamp, Map<String, String> properties) {
        this.connectorName = connectorName;
        this.connectTopicname = connectTopicName;
        this.taskClass = taskClass;
        this.connectorClass = connectorClass;
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

    public String getTaskClass() {
        return taskClass;
    }

    public void setTaskClass(String taskClass) {
        this.taskClass = taskClass;
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

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "WorkerTask{" +
                "connectorName='" + connectorName + '\'' +
                ", connectTopicName='" + connectTopicname + '\'' +
                ", taskClass='" + taskClass + '\'' +
                ", connectorClass='" + connectorClass + '\'' +
                ", updateTimestamp=" + updateTimestamp +
                ", properties=" + properties +
                '}';
    }
}
