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
package org.apache.rocketmq.dashboard.cli;


import picocli.CommandLine.Option;

/**
 * Global options
 */
public class GlobalOptions {

    @Option(names = {"--yes"}, description = "Skip confirmation prompts")
    boolean yes;

    @Option(names = {"--force"}, description = "Force execution of dangerous operations (required for L3)")
    boolean force;

    @Option(names = {"--dry-run"}, description = "Preview changes without executing")
    boolean dryRun;

    public boolean isYes() {
        return yes;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isForce() {
        return force;
    }
}
