/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageRequestModeReaderTest {
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final RemotingClient transport = mock(RemotingClient.class);
    private final MessageRequestModeReader reader = new MessageRequestModeReader(new ObjectMapper());
    private final RemotingCommand response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);

    @BeforeEach
    void setUp() throws Exception {
        var implementation = mock(DefaultMQAdminExtImpl.class);
        var instance = mock(MQClientInstance.class);
        var api = mock(MQClientAPIImpl.class);
        when(admin.getDefaultMQAdminExtImpl()).thenReturn(implementation);
        when(implementation.getMqClientInstance()).thenReturn(instance);
        when(instance.getMQClientAPIImpl()).thenReturn(api);
        when(api.getRemotingClient()).thenReturn(transport);
        when(transport.invokeSync(anyString(), any(), anyLong())).thenReturn(response);
    }

    private void body(String value) { response.setBody(value.getBytes(StandardCharsets.UTF_8)); }

    @Test
    void usesExistingSdkTransportAndSelectsOnlyTheRequestedGroup() throws Exception {
        when(admin.isVipChannelEnabled()).thenReturn(true);
        body("""
                {"messageRequestModeMap":{"orders":{"group-a":{"topic":"orders","consumerGroup":"group-a",
                "mode":"POP","popShareQueueNum":-1},"other":{"mode":"PULL"}}}}
                """);
        var result = reader.read(admin, "master:10911", "orders", "group-a");
        assertThat(result.get("mode").asText()).isEqualTo("POP");
        assertThat(result.toString()).doesNotContain("other");
        var request = org.mockito.ArgumentCaptor.forClass(RemotingCommand.class);
        verify(transport).invokeSync(eq("master:10909"), request.capture(), eq(5000L));
        assertThat(request.getValue().getCode()).isEqualTo(RequestCode.GET_ALL_MESSAGE_REQUEST_MODE);
    }

    @Test
    void absenceOfTopicOrGroupIsAnInheritedDefault() throws Exception {
        body("{\"messageRequestModeMap\":{}}");
        assertThat(reader.read(admin, "master:10911", "orders", "group-a")).isNull();
        body("{\"messageRequestModeMap\":{\"orders\":{}}}");
        assertThat(reader.read(admin, "master:10911", "orders", "group-a")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"messageRequestModeMap\":[]}",
        "{\"messageRequestModeMap\":{\"orders\":null}}"})
    void invalidEnvelopesCannotBeMistakenForAnAbsentOverride(String json) {
        body(json);
        assertThatThrownBy(() -> reader.read(admin, "master:10911", "orders", "group-a"))
                .hasMessageContaining("invalid");
    }

    @Test
    void brokerErrorsAndMissingBodiesAreNotDefaultMode() throws Exception {
        assertThatThrownBy(() -> reader.read(admin, "master:10911", "orders", "group-a"))
                .hasMessageContaining("did not return");
        body("{\"messageRequestModeMap\":{}}");
        response.setCode(ResponseCode.NO_PERMISSION);
        assertThatThrownBy(() -> reader.read(admin, "master:10911", "orders", "group-a"))
                .hasMessageContaining("did not return");
        when(transport.invokeSync(anyString(), any(), anyLong())).thenReturn(null);
        assertThatThrownBy(() -> reader.read(admin, "master:10911", "orders", "group-a"))
                .hasMessageContaining("did not return");
    }
}
