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

import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.trace.MessageTraceGraph;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.apache.rocketmq.dashboard.service.MessageTraceService;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messageTrace")
@Permission
@RequiredArgsConstructor
@Slf4j
public class MessageTraceController {

    private final MessageService messageService;
    private final MessageTraceService messageTraceService;

    @GetMapping(value = "/viewMessage.query")
    public ResponseEntity<Object> viewMessage(@RequestParam(required = false) String topic, @RequestParam String msgId) {
        Map<String, Object> messageViewMap = Maps.newHashMap();
        Pair<MessageView, List<MessageTrack>> messageViewListPair = messageService.viewMessage(topic, msgId);
        messageViewMap.put("messageView", messageViewListPair.getObject1());
        return ResponseEntity.ok(messageViewMap);
    }

    @GetMapping(value = "/viewMessageTraceDetail.query")
    public ResponseEntity<Object> viewTraceMessages(@RequestParam String msgId) {
        return ResponseEntity.ok(messageTraceService.queryMessageTraceKey(msgId));
    }

    @GetMapping(value = "/viewMessageTraceGraph.query")
    public ResponseEntity<MessageTraceGraph> viewMessageTraceGraph(@RequestParam String msgId,
                                                   @RequestParam(required = false) String traceTopic) {
        return ResponseEntity.ok(messageTraceService.queryMessageTraceGraph(msgId, traceTopic));
    }
}
