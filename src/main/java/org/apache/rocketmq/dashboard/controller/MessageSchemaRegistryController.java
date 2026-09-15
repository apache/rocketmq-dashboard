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

import org.apache.rocketmq.dashboard.model.MessageSchemaReport;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.MessageSchemaRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/schema")
@Permission
public class MessageSchemaRegistryController {

    @Autowired
    private MessageSchemaRegistryService messageSchemaRegistryService;

    @RequestMapping(value = "/overview.query", method = RequestMethod.GET)
    @ResponseBody
    public MessageSchemaReport getSchemaOverview(@RequestParam("topic") String topic) {
        return messageSchemaRegistryService.getSchemaReport(topic);
    }

    @RequestMapping(value = "/compatibility/test.do", method = RequestMethod.POST)
    @ResponseBody
    public MessageSchemaReport testCompatibility(
        @RequestParam("topic") String topic,
        @RequestParam(value = "compatibilityMode", required = false, defaultValue = "BACKWARD") String compatibilityMode,
        @RequestBody String newSchemaDefinition) {
        return messageSchemaRegistryService.testSchemaEvolution(topic, newSchemaDefinition, compatibilityMode);
    }

    @RequestMapping(value = "/payload/validate.do", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> validatePayload(
        @RequestParam("topic") String topic,
        @RequestBody String payload) {
        boolean valid = messageSchemaRegistryService.validatePayload(topic, payload);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        result.put("topic", topic);
        return result;
    }
}
