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
