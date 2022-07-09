package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.model.connect.WorkerConnector;
import org.apache.rocketmq.dashboard.model.connect.WorkerInfo;
import org.apache.rocketmq.dashboard.model.connect.WorkerTask;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.ConnectService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/connect")
@Permission
public class ConnectController {

    @Resource
    private ConnectService connectService;

    @RequestMapping(value = "/WorkerConnectors.query", method = RequestMethod.GET)
    @ResponseBody
    public Object workerConnectorList() {
        return connectService.queryWorkerConnectorList();
    }

    @RequestMapping(value = "/worker.query", method = RequestMethod.GET)
    @ResponseBody
    public Object workerList() {
        return connectService.queryWorkerList();
    }

    @RequestMapping(value = "/workerTasks.query", method = RequestMethod.GET)
    @ResponseBody
    public Object workerTaskList() {
        return connectService.queryWorkerTaskList();
    }


    @RequestMapping(value = "/createConnector.do", method = RequestMethod.POST)
    @ResponseBody
    public String createConnector(@RequestBody Map<String, String> workerConnector) {
        return connectService.createConnector(workerConnector);
    }

    @RequestMapping(value = "/stopConnector.do", method = RequestMethod.GET)
    @ResponseBody
    public String stopConnector(@RequestParam(value = "name") String connectorName) {
        return connectService.stopConnector(connectorName);
    }

    @RequestMapping(value = "/stopAllConnectors.do", method = RequestMethod.GET)
    @ResponseBody
    public String stopAllConnectors() {
        return connectService.stopAllConnectors();
    }

    @RequestMapping(value = "/reloadConnector.do", method = RequestMethod.GET)
    @ResponseBody
    public String reloadConnector() {
        return connectService.reloadAllConnectors();
    }

    @RequestMapping(value = "/connectorStatus.query", method = RequestMethod.GET)
    @ResponseBody
    public Object getConnectorStatus(@RequestParam(value = "name") String connectorName) {

        String connectorStatus = connectService.getConnectorStatus(connectorName);
        return connectorStatus;
    }

    @RequestMapping(value = "/allocatedConnectors.query", method = RequestMethod.GET)
    @ResponseBody
    public void allocatedConnectors(@RequestParam(value = "ipAddr") String ipAddr) {
    }

    @RequestMapping(value = "/allocatedTasks.query", method = RequestMethod.GET)
    @ResponseBody
    public void allocatedTasks(@RequestParam(value = "ipAddr") String ipAddr) {
    }



}
