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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.ops.OpsConnectionSettings;
import org.apache.rocketmq.studio.ops.OpsRuntimeConnection;
import org.apache.rocketmq.studio.ops.OpsRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsDefaultClientTest {

    @Test
    void disabledModeKeepsTheExternalEndpointAndLegacyFactory() {
        OpsRuntimeConnection runtime = mock(OpsRuntimeConnection.class);
        OpsRuntimeProperties properties = new OpsRuntimeProperties();
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        OpsDefaultClient client = new OpsDefaultClient(runtime, properties, adminFactory, mock(MqClientPool.class));

        assertThat(client.namesrvAddr("env-namesrv:9876")).isEqualTo("env-namesrv:9876");
        client.execute("env-namesrv:9876", null, "admin", ignored -> "old");

        verify(adminFactory).execute(eq("env-namesrv:9876"), eq(null), eq("admin"), any());
        verifyNoInteractions(runtime);
    }

    @Test
    void enabledModeRoutesDefaultCallsThroughThePersistedConnection() {
        OpsRuntimeConnection runtime = mock(OpsRuntimeConnection.class);
        OpsRuntimeProperties properties = new OpsRuntimeProperties();
        properties.setEnabled(true);
        OpsConnectionSettings settings = new OpsConnectionSettings(
                List.of("first:9876", "second:9876"), "second:9876", true, false);
        when(runtime.current()).thenReturn(settings);
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        MqClientPool pool = mock(MqClientPool.class);
        OpsDefaultClient client = new OpsDefaultClient(runtime, properties, adminFactory, pool);

        assertThat(client.namesrvAddr("env-namesrv:9876")).isEqualTo("second:9876");
        client.execute("env-namesrv:9876", null, "admin", ignored -> "current");
        client.withProducer("env-namesrv:9876", ignored -> "sent");

        verify(adminFactory).executeDefault(eq(settings), eq(null), eq("admin"), any());
        verify(pool).withProducerDefault(eq(settings), eq(null), eq("anonymous"), any());
    }

    @Test
    void selectedConnectionUsesOneSnapshotForEndpointAndActualClient() {
        OpsRuntimeConnection runtime = mock(OpsRuntimeConnection.class);
        OpsRuntimeProperties properties = new OpsRuntimeProperties();
        properties.setEnabled(true);
        OpsConnectionSettings first = new OpsConnectionSettings(
                List.of("first:9876", "second:9876"), "first:9876", false, false);
        OpsConnectionSettings second = new OpsConnectionSettings(
                List.of("first:9876", "second:9876"), "second:9876", false, false);
        when(runtime.current()).thenReturn(first, second);
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        OpsDefaultClient client = new OpsDefaultClient(runtime, properties, adminFactory, mock(MqClientPool.class));

        OpsDefaultClient.Selection selection = client.select("env-namesrv:9876");
        selection.execute(null, "anonymous", ignored -> "connected");

        assertThat(selection.namesrvAddr()).isEqualTo("first:9876");
        verify(adminFactory).executeDefault(eq(first), eq(null), eq("anonymous"), any());
        org.mockito.Mockito.verify(runtime).current();
    }
}
