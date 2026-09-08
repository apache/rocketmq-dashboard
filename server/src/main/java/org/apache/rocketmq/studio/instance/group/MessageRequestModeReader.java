/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageRequestModeReader {
    private final ObjectMapper mapper;

    public JsonNode read(DefaultMQAdminExt admin, String address, String topic, String group) throws Exception {
        // Reuse the instance's authenticated SDK transport; MQAdminExt has no read wrapper for this command.
        var request = RemotingCommand.createRequestCommand(RequestCode.GET_ALL_MESSAGE_REQUEST_MODE, null);
        var response = admin.getDefaultMQAdminExtImpl().getMqClientInstance().getMQClientAPIImpl()
                .getRemotingClient().invokeSync(MixAll.brokerVIPChannel(admin.isVipChannelEnabled(), address), request, 5000);
        if (response == null || response.getCode() != ResponseCode.SUCCESS || response.getBody() == null) {
            throw new BusinessException(502, "Broker did not return message request mode configuration");
        }
        JsonNode root = mapper.readTree(response.getBody());
        JsonNode modes = root == null ? null : root.get("messageRequestModeMap");
        if (modes == null || !modes.isObject()) {
            throw new BusinessException(502, "Broker returned invalid message request mode configuration");
        }
        JsonNode groups = modes.get(topic);
        if (groups == null) {
            return null;
        }
        if (!groups.isObject()) {
            throw new BusinessException(502, "Broker returned invalid topic request mode configuration");
        }
        return groups.get(group);
    }
}
