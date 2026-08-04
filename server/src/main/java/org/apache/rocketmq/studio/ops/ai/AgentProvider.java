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

/**
 * Gateway abstraction for agent runtimes. Implementations spawn the vendor CLI
 * (claude code / qoder) as a subprocess and pass credentials through the child
 * process environment — never through argv or persisted storage.
 */
public interface AgentProvider {

    String engine();

    boolean available();

    String complete(LlmConfigVO config, String prompt, String modelOverride);

    /** Streams completion tokens; default falls back to a single chunk via complete(). */
    default void stream(LlmConfigVO config, String prompt, String modelOverride,
                        java.util.function.Consumer<String> tokenConsumer) {
        tokenConsumer.accept(complete(config, prompt, modelOverride));
    }
}
