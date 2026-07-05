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

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Generates bash/zsh completion scripts for rmqctl.
 * Run: java -cp ... org.apache.rocketmq.dashboard.cli.GenerateCompletion [bash|zsh]
 */
@Command(name = "generate-completion", description = "Generate shell completion script (bash or zsh)")
public class GenerateCompletion implements Runnable {

    @Override
    public void run() {
        generateCompletion("bash");
    }

    public static void main(String[] args) {
        String shell = args.length > 0 ? args[0] : "bash";
        generateCompletion(shell);
    }

    private static void generateCompletion(String shell) {
        CommandLine cmd = new CommandLine(new RmqctlCommand());
        String commandName = "rmqctl";
        String script = AutoComplete.bash(commandName, cmd);
        if ("zsh".equals(shell)) {
            script = script.replace("bash", "zsh")
                .replace("_rmqctl_completion", "_rmqctl")
                .replace("complete -o", "compdef _rmqctl rmqctl\n\ncomplete -o");
        }
        System.out.println("# rmqctl " + shell + " completion script");
        System.out.println("# Source this file in your ~/." + shell + "rc:");
        System.out.println("#   source <(rmqctl generate-completion " + shell + ")");
        System.out.println(script);
    }
}
