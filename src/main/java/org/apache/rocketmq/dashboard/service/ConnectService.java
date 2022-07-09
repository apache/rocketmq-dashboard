package org.apache.rocketmq.dashboard.service;


import org.apache.rocketmq.dashboard.model.connect.WorkerConnector;
import org.apache.rocketmq.dashboard.model.connect.WorkerInfo;
import org.apache.rocketmq.dashboard.model.connect.WorkerTask;

import java.util.List;
import java.util.Map;

public interface ConnectService {

    List<WorkerConnector> queryWorkerConnectorList();

    List<WorkerInfo> queryWorkerList();

    List<WorkerTask> queryWorkerTaskList();


    String stopConnector(String connectorName);

    String stopAllConnectors();

    String reloadAllConnectors();

    String createConnector(Map<String, String> workerConnector);


    String getAllocatedConnectors(String ipAddr);

    String getAllocatedTasks(String ipAddr);

    String getConnectorStatus(String connectorName);

}
