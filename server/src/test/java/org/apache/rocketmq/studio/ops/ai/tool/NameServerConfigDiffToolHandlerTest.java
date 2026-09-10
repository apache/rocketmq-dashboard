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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.cluster.nameserver.NameServerConfigDiffService;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerConfigDiffVO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NameServerConfigDiffToolHandlerTest {

    @Test
    void executeShouldDelegateAndReturnTheDiffResult() {
        NameServerConfigDiffService service = mock(NameServerConfigDiffService.class);
        NameServerConfigDiffVO diff = new NameServerConfigDiffVO();
        when(service.compare("cluster-a")).thenReturn(diff);

        Object output = new NameServerConfigDiffToolHandler(service)
                .execute(Map.of("cluster", "cluster-a"));

        assertThat(output).isSameAs(diff);
        verify(service).compare("cluster-a");
    }

    @Test
    void handlerNameShouldBeRmqNameserverConfigDiff() {
        assertThat(new NameServerConfigDiffToolHandler(mock(NameServerConfigDiffService.class)).name())
                .isEqualTo("rmq.nameserver.config.diff");
    }

    @Test
    void executeShouldForwardAMissingClusterAsNull() {
        NameServerConfigDiffService service = mock(NameServerConfigDiffService.class);
        NameServerConfigDiffVO diff = new NameServerConfigDiffVO();
        when(service.compare(null)).thenReturn(diff);

        assertThat(new NameServerConfigDiffToolHandler(service).execute(Map.of())).isSameAs(diff);
        verify(service).compare(null);
    }
}
