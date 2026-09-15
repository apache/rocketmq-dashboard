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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dlq;

import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQClearInput;
import org.junit.jupiter.api.Test;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DLQClearToolHandlerTest {

    @Test
    void deletesOnlyTheGroupDeadLetterTopicInTheBoundInstance() {
        MetadataService metadata = mock(MetadataService.class);
        DLQService dlq = mock(DLQService.class);
        DLQClearToolHandler handler = new DLQClearToolHandler(metadata, dlq);

        handler.execute(new DLQClearInput("input-cluster", "group-a"), context("instance-a"));

        verify(metadata).deleteTopic("instance-a", "%DLQ%group-a");
        verifyNoMoreInteractions(metadata);
        verifyNoInteractions(dlq);
    }
}
