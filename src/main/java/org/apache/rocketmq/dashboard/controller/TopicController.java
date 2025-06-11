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

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.rocketmq.dashboard.model.request.SendTopicMessageRequest;
import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/topic")
@Permission
@RequiredArgsConstructor
@Slf4j
public class TopicController {

    private final TopicService topicService;
    private final ConsumerService consumerService;

    @GetMapping(value = "/list.query")
    public ResponseEntity<Object> list(@RequestParam(value = "skipSysProcess", required = false) Boolean skipSysProcess,
                                      @RequestParam(value = "skipRetryAndDlq", required = false) Boolean skipRetryAndDlq) {
        boolean skipSysProcessFound = Optional.ofNullable(skipSysProcess).orElse(false);
        boolean skipRetryAndDlqFound = Optional.ofNullable(skipRetryAndDlq).orElse(false);
        return ResponseEntity.ok(topicService.fetchAllTopicList(skipSysProcessFound, skipRetryAndDlqFound));
    }

    @GetMapping(value = "/stats.query")
    public ResponseEntity<Object> stats(@RequestParam String topic) {
        return ResponseEntity.ok(topicService.stats(topic));
    }

    @GetMapping(value = "/route.query")
    public ResponseEntity<Object> route(@RequestParam String topic) {
        return ResponseEntity.ok(topicService.route(topic));
    }


    @PostMapping(value = "/createOrUpdate.do")
    public ResponseEntity<Object> topicCreateOrUpdateRequest(@RequestBody TopicConfigInfo topicCreateOrUpdateRequest) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(topicCreateOrUpdateRequest.getBrokerNameList()) || CollectionUtils.isNotEmpty(topicCreateOrUpdateRequest.getClusterNameList()),
                "clusterName or brokerName can not be all blank");
        log.info("op=look topicCreateOrUpdateRequest={}", JsonUtil.objectToString(topicCreateOrUpdateRequest));
        topicService.createOrUpdate(topicCreateOrUpdateRequest);
        return ResponseEntity.ok(true);
    }

    @GetMapping(value = "/queryConsumerByTopic.query")
    public ResponseEntity<Object> queryConsumerByTopic(@RequestParam String topic) {
        return ResponseEntity.ok(consumerService.queryConsumeStatsListByTopicName(topic));
    }

    @GetMapping(value = "/queryTopicConsumerInfo.query")
    public ResponseEntity<Object> queryTopicConsumerInfo(@RequestParam String topic) {
        return ResponseEntity.ok(topicService.queryTopicConsumerInfo(topic));
    }

    @GetMapping(value = "/examineTopicConfig.query")
    public ResponseEntity<Object> examineTopicConfig(@RequestParam String topic) {
        return ResponseEntity.ok(topicService.examineTopicConfig(topic));
    }

    @PostMapping(value = "/sendTopicMessage.do")
    public ResponseEntity<Object> sendTopicMessage(
            @RequestBody SendTopicMessageRequest sendTopicMessageRequest) {
        return ResponseEntity.ok(topicService.sendTopicMessageRequest(sendTopicMessageRequest));
    }

    @PostMapping(value = "/deleteTopic.do")
    public ResponseEntity<Object> delete(@RequestParam String topic, @RequestParam(required = false) String clusterName) {
        return ResponseEntity.ok(topicService.deleteTopic(topic, clusterName));
    }

    @PostMapping(value = "/deleteTopicByBroker.do")
    public ResponseEntity<Object> deleteTopicByBroker(@RequestParam String brokerName, @RequestParam String topic) {
        return ResponseEntity.ok(topicService.deleteTopicInBroker(brokerName, topic));
    }
}
