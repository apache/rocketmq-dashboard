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
package org.apache.rocketmq.studio.ops.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerTest {

    @Test
    void chatDelegatesToAiService() {
        AiService aiService = mock(AiService.class);
        ChatDTO request = ChatDTO.builder().message("List topics").build();
        SseEmitter emitter = new SseEmitter();
        when(aiService.chat(request)).thenReturn(emitter);

        SseEmitter result = new AiController(aiService).chat(request);

        assertThat(result).isSameAs(emitter);
        verify(aiService).chat(request);
    }

    @Test
    void executeDelegatesToAiService() {
        AiService aiService = mock(AiService.class);
        AiCommandDTO command = AiCommandDTO.builder().command("list_topics").build();
        AiExecuteResultVO output = AiExecuteResultVO.builder()
                .success(true)
                .result("done")
                .build();
        when(aiService.execute(command)).thenReturn(output);

        assertThat(new AiController(aiService).execute(command).getData()).isSameAs(output);
        verify(aiService).execute(command);
    }
}
