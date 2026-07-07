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
package com.rocketmq.studio.ops.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class LlmGatewayStub implements LlmGateway {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(ChatDTO request) {
        SseEmitter emitter = new SseEmitter(60_000L);

        executor.execute(() -> {
            try {
                String[] tokens = ("This is a stub response for: " + request.getMessage()).split(" ");
                for (String token : tokens) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("{\"text\": \"" + token + " \"}"));
                    Thread.sleep(100);
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (IOException | InterruptedException e) {
                log.error("Error streaming stub response", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public String execute(AiCommandDTO command) {
        log.info("Stub executing AI command: {}", command.getCommand());
        return "Command executed successfully (stub)";
    }
}
