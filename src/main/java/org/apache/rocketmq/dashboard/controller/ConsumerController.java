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
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.dashboard.model.ConnectionInfo;
import org.apache.rocketmq.dashboard.model.ConsumerGroupRollBackStat;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.model.request.DeleteSubGroupRequest;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/consumer")
@Permission
@RequiredArgsConstructor
@Slf4j
public class ConsumerController {

    private final ConsumerService consumerService;

    @GetMapping(value = "/groupList.query")
    public ResponseEntity<Object> list(@RequestParam(value = "skipSysGroup", required = false) Boolean skipSysGroup) {
        boolean skipSysGroupFound = Optional.ofNullable(skipSysGroup).orElse(false);
        return ResponseEntity.ok(consumerService.queryGroupList(skipSysGroupFound));
    }

    @GetMapping(value = "/group.query")
    public ResponseEntity<Object> groupQuery(@RequestParam String consumerGroup) {
        return ResponseEntity.ok(consumerService.queryGroup(consumerGroup));
    }

    @PostMapping(value = "/resetOffset.do")
    public ResponseEntity<Object> resetOffset(@RequestBody ResetOffsetRequest resetOffsetRequest) {
        return ResponseEntity.ok(callResetOffset(resetOffsetRequest));
    }

    @PostMapping(value = "/skipAccumulate.do")
    public ResponseEntity<Object> skipAccumulate(@RequestBody ResetOffsetRequest resetOffsetRequest) {
        return ResponseEntity.ok(callResetOffset(resetOffsetRequest));
    }

    @PostMapping(value = "/examineSubscriptionGroupConfig.query")
    public ResponseEntity<Object> examineSubscriptionGroupConfig(@RequestParam String consumerGroup) {
        return ResponseEntity.ok(consumerService.examineSubscriptionGroupConfig(consumerGroup));
    }

    @PostMapping(value = "/deleteSubGroup.do")
    public ResponseEntity<Object> deleteSubGroup(@RequestBody DeleteSubGroupRequest deleteSubGroupRequest) {
        return ResponseEntity.ok(consumerService.deleteSubGroup(deleteSubGroupRequest));
    }

    @PostMapping(value = "/createOrUpdate.do")
    public ResponseEntity<Object> consumerCreateOrUpdateRequest(@RequestBody ConsumerConfigInfo consumerConfigInfo) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(consumerConfigInfo.getBrokerNameList()) || CollectionUtils.isNotEmpty(consumerConfigInfo.getClusterNameList()),
                "clusterName or brokerName can not be all blank");
        return ResponseEntity.ok(consumerService.createAndUpdateSubscriptionGroupConfig(consumerConfigInfo));
    }

    @GetMapping(value = "/fetchBrokerNameList.query")
    public ResponseEntity<Object> fetchBrokerNameList(@RequestParam String consumerGroup) {
        return ResponseEntity.ok(consumerService.fetchBrokerNameSetBySubscriptionGroup(consumerGroup));
    }

    @GetMapping(value = "/queryTopicByConsumer.query")
    public ResponseEntity<Object> queryConsumerByTopic(@RequestParam String consumerGroup) {
        return ResponseEntity.ok(consumerService.queryConsumeStatsListByGroupName(consumerGroup));
    }

    @GetMapping(value = "/consumerConnection.query")
    public ResponseEntity<Object> consumerConnection(@RequestParam(required = false) String consumerGroup) {
        ConsumerConnection consumerConnection = consumerService.getConsumerConnection(consumerGroup);
        consumerConnection.setConnectionSet(ConnectionInfo.buildConnectionInfoHashSet(consumerConnection.getConnectionSet()));
        return ResponseEntity.ok(consumerConnection);
    }

    @GetMapping(value = "/consumerRunningInfo.query")
    public ResponseEntity<Object> getConsumerRunningInfo(@RequestParam String consumerGroup, @RequestParam String clientId,
                                         @RequestParam boolean jStack) {
        return ResponseEntity.ok(consumerService.getConsumerRunningInfo(consumerGroup, clientId, jStack));
    }

    private Map<String, ConsumerGroupRollBackStat> callResetOffset(ResetOffsetRequest resetOffsetRequest) {
        log.info("op=look resetOffsetRequest={}", JsonUtil.objectToString(resetOffsetRequest));
        return consumerService.resetOffset(resetOffsetRequest);
    }
}
