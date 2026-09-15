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
package org.apache.rocketmq.studio.cluster.lifecycle;

/** Lifecycle actions that must be delegated to the deployment control plane. */
public enum LifecycleOperation {
    BROKER_RESTART("broker-restart", "RESTART_BROKER", "BROKER"),
    NAMESERVER_RESTART("nameserver-restart", "RESTART_NAMESERVER", "NAMESERVER"),
    NAMESERVER_UPGRADE("nameserver-upgrade", "UPGRADE_NAMESERVER", "NAMESERVER"),
    NAMESERVER_DELETE("nameserver-delete", "DELETE_NAMESERVER", "NAMESERVER"),
    PROXY_RESTART("proxy-restart", "RESTART_PROXY", "PROXY");

    private final String commandName;
    private final String auditOperation;
    private final String resourceType;

    LifecycleOperation(String commandName, String auditOperation, String resourceType) {
        this.commandName = commandName;
        this.auditOperation = auditOperation;
        this.resourceType = resourceType;
    }

    public String commandName() {
        return commandName;
    }

    public String auditOperation() {
        return auditOperation;
    }

    public String resourceType() {
        return resourceType;
    }
}
