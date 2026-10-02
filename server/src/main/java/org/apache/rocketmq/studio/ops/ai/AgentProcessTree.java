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

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Stream;

/** Process-tree termination shared by agent providers and conversation cancellation. */
@Slf4j
public final class AgentProcessTree {

    private AgentProcessTree() {
    }

    /** Force-stops descendants before the root so a CLI failure cannot orphan its MCP server. */
    public static void destroyForcibly(Process root, String context) {
        destroyForcibly(root, descendants(root, context));
    }

    /** Force-stops a captured descendant set before its root process. */
    public static void destroyForcibly(Process root, Iterable<ProcessHandle> descendants) {
        destroyDescendants(descendants);
        root.destroyForcibly();
    }

    /** Force-stops a previously captured descendant set without touching its root process. */
    public static void destroyDescendants(Iterable<ProcessHandle> descendants) {
        descendants.forEach(ProcessHandle::destroyForcibly);
    }

    /** Snapshots descendants while the root still owns them. */
    public static List<ProcessHandle> descendants(Process root, String context) {
        try (Stream<ProcessHandle> descendants = root.descendants()) {
            return descendants.toList();
        } catch (RuntimeException exception) {
            log.warn("could not enumerate descendants of {}: {}", context, exception.toString());
            return List.of();
        }
    }
}
