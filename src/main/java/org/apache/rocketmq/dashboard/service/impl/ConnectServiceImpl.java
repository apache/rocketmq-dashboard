package org.apache.rocketmq.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.dashboard.model.connect.WorkerConnector;
import org.apache.rocketmq.dashboard.model.connect.WorkerInfo;
import org.apache.rocketmq.dashboard.model.connect.WorkerTask;
import org.apache.rocketmq.dashboard.service.ConnectService;
import org.apache.rocketmq.dashboard.util.HttpRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ConnectServiceImpl implements ConnectService {
    private static final Logger log = LoggerFactory.getLogger(ConnectServiceImpl.class);

    @Value("${rocketmq.config.connectAPIAddr}")
    private static String ipAddr;

    @Override
    public List<WorkerInfo> queryWorkerList() {
        String status = HttpRequestUtil.requestString("/getClusterInfo");
        JSONArray jsonArray = JSON.parseArray(status);
        List<WorkerInfo> workerInfoList = new ArrayList<>();
        for (Iterator<Object> iterator = jsonArray.iterator(); iterator.hasNext(); ) {
            WorkerInfo workerInfo = new WorkerInfo();
            String next = (String) iterator.next();
            int seperator = next.indexOf("@", 0);
            String worker = next.substring(0, seperator);
            String namesrv = next.substring(seperator + 1, next.length());

            workerInfo.setIpAddr(worker);
            workerInfo.setNamesrvAddr(namesrv);

            workerInfoList.add(workerInfo);
        }
        return workerInfoList;
    }

    @Override
    public List<WorkerConnector> queryWorkerConnectorList() {
        String status = HttpRequestUtil.requestString("/getConfigInfo");
        JSONObject jsonObject = JSON.parseObject(status).getJSONObject("connectorConfigs");
        List<WorkerConnector> workerConnectorList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            JSONObject properties = ((JSONObject) entry.getValue()).getJSONObject("properties");
            properties.put("connector-name", entry.getKey());

            WorkerConnector workerConnector = JSON.parseObject(properties.toJSONString(), WorkerConnector.class);

            Map<String, String> finalProperties = (Map<String, String>) JSONObject.parse(properties.toJSONString());
            workerConnector.setProperties(finalProperties);

            workerConnectorList.add(workerConnector);
        }
        return workerConnectorList;
    }

    @Override
    public List<WorkerTask> queryWorkerTaskList() {
        String status = HttpRequestUtil.requestString("/getConfigInfo");
        JSONObject jsonObject = JSON.parseObject(status).getJSONObject("taskConfigs");
        List<WorkerTask> workerTaskList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            JSONObject properties = ((JSONArray) entry.getValue()).getJSONObject(0).getJSONObject("properties");
            properties.put("connector-name", entry.getKey());

            WorkerTask workerTask = JSON.parseObject(properties.toJSONString(), WorkerTask.class);

            Map<String, String> finalProperties = (Map<String, String>) JSONObject.parse(properties.toJSONString());
            workerTask.setProperties(finalProperties);

            workerTaskList.add(workerTask);
        }
        return workerTaskList;
    }


    public static void test1() {
        System.out.println(ipAddr);
    }

    @Override
    public String getConnectorStatus(String connectorName) {
        return HttpRequestUtil.requestString("/connectors/" + connectorName + "/status");
    }

    @Override
    public String createConnector(Map<String, String> workerConnector) {
        String url = null;
        JSONObject jsonObject = null;
        String ipAddr = workerConnector.get("clusterAddr").trim();
        String workerPort = workerConnector.get("workerPort").trim();
        String connectorName = workerConnector.get("connectorName").trim();
        url = "http://" + ipAddr + ":" + workerPort + "/connectors/" + connectorName;

        String properties = workerConnector.get("Properties").trim();
        if (properties != null) {
            jsonObject = JSONObject.parseObject(properties);
        }

        try {
            jsonObject.put("connect-topicname", workerConnector.get("connectTopicname").trim());
            jsonObject.put("connector-class", workerConnector.get("connectorClass").trim());
        } catch (Exception e) {
            throw new RuntimeException("BAD PARAMS,         " + e);
        }

        return HttpRequestUtil.postRequest(url, jsonObject);
    }

    @Override
    public String stopConnector(String connectorName) {
        return HttpRequestUtil.requestString("/connectors/" + connectorName + "/stop");
    }

    @Override
    public String stopAllConnectors() {
        return HttpRequestUtil.requestString("/connectors/stopAll");
    }

    @Override
    public String reloadAllConnectors() {
        return HttpRequestUtil.requestString("/plugin/reload");
    }

    @Override
    public String getAllocatedConnectors(String ipAddr) {
        String status = HttpRequestUtil.requestString("/getAllocatedConnectors");

        return null;
    }

    @Override
    public String getAllocatedTasks(String ipAddr) {
        String status = HttpRequestUtil.requestString("/getAllocatedTasks");

        return null;
    }


}
