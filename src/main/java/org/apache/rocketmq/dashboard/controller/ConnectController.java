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
package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.ConnectService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
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
