package org.apache.rocketmq.dashboard.model.connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WorkerInfo {
    private static final Logger log = LoggerFactory.getLogger(WorkerInfo.class);

    private String ipAddr;

    private String namesrvAddr;

    private List<WorkerConnector> workingConnectors;

    private List<WorkerTask> existingTasks;

    public WorkerInfo(String ipAddr, String namesrvAddr, List<WorkerConnector> workingConnectors, List<WorkerTask> existingTasks) {
        this.ipAddr = ipAddr;
        this.namesrvAddr = namesrvAddr;
        this.workingConnectors = workingConnectors;
        this.existingTasks = existingTasks;
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

    public List<WorkerConnector> getWorkingConnectors() {
        return workingConnectors;
    }

    public void setWorkingConnectors(List<WorkerConnector> workingConnectors) {
        this.workingConnectors = workingConnectors;
    }

    public List<WorkerTask> getExistingTasks() {
        return existingTasks;
    }

    public void setExistingTasks(List<WorkerTask> existingTasks) {
        this.existingTasks = existingTasks;
    }

    @Override
    public String toString() {
        return "WorkerInfo{" +
                "ipAddr='" + ipAddr + '\'' +
                ", namesrvAddr='" + namesrvAddr + '\'' +
                ", workingConnectors=" + workingConnectors +
                ", existingTasks=" + existingTasks +
                '}';
    }
}
