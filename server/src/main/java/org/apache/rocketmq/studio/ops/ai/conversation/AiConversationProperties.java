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
package org.apache.rocketmq.studio.ops.ai.conversation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Tunables for AI conversation persistence, retention and the hosted-agent workspace. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "studio.ai.conversation")
public class AiConversationProperties {

    /**
     * Number of days conversations, runs and events are retained. A non-positive value
     * disables cleanup.
     */
    private int retentionDays = 90;

    /** Maximum expired rows deleted per database statement. */
    private int cleanupBatchSize = 500;

    /** Maximum delete batches performed by one scheduled cleanup pass. */
    private int cleanupMaxBatches = 20;

    /** How often the retention and orphan-run sweep runs. */
    private Duration cleanupInterval = Duration.ofHours(24);

    /**
     * A run still marked QUEUED or RUNNING after this long, with no live handle in the
     * registry, is orphaned (its worker died without writing a terminal state) and gets
     * reaped. Swept by the same scheduled pass as retention.
     */
    private Duration orphanRunTimeout = Duration.ofMinutes(10);

    /** Grace period between SIGTERM and SIGKILL when stopping an agent subprocess. */
    private Duration stopGrace = Duration.ofSeconds(3);

    /** Root directory for per-conversation agent workspaces (rmqctl config, HOME, cwd). */
    private String workspaceDir = "/tmp/rocketmq-studio-ai";

    /** When false, the agent runs without MCP tools and degrades to plain chat. */
    private boolean rmqctlEnabled = true;

    /**
     * Studio base URL that rmqctl calls back into. Blank means "derive from
     * {@code server.port} on loopback", so the port has a single source of truth.
     * Any explicit override must stay loopback or use https: rmqctl rejects plain HTTP
     * for a non-loopback host.
     */
    private String rmqctlServerUrl = "";

    /** Per-tool-call timeout handed to rmqctl as --timeout. */
    private Duration rmqctlTimeout = Duration.ofSeconds(60);
}
