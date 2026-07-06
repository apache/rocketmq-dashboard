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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GenerateCompletionTest {

    private ByteArrayOutputStream capturedOut;
    private PrintStream originalOut;

    @Before
    public void setUp() {
        capturedOut = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(capturedOut));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testRunDefaultBash() {
        GenerateCompletion cmd = new GenerateCompletion();
        cmd.run();
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("# rmqctl bash completion script"));
        Assert.assertTrue(output.contains("source"));
        Assert.assertTrue(output.contains("rmqctl"));
    }

    @Test
    public void testMainWithBashArg() {
        GenerateCompletion.main(new String[]{"bash"});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("# rmqctl bash completion script"));
        Assert.assertTrue(output.contains("bashrc"));
    }

    @Test
    public void testMainWithZshArg() {
        GenerateCompletion.main(new String[]{"zsh"});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("# rmqctl zsh completion script"));
        Assert.assertTrue(output.contains("zshrc"));
        Assert.assertTrue(output.contains("compdef"));
    }

    @Test
    public void testMainWithNoArgs() {
        GenerateCompletion.main(new String[]{});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("# rmqctl bash completion script"));
    }

    @Test
    public void testBashCompletionContainsCompletionFunction() {
        GenerateCompletion.main(new String[]{"bash"});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("_rmqctl_completion") || output.contains("_rmqctl"));
    }

    @Test
    public void testZshCompletionContainsCompdef() {
        GenerateCompletion.main(new String[]{"zsh"});
        String output = capturedOut.toString();
        // zsh mode replaces "bash" with "zsh" and adds compdef if "complete -o" is found
        Assert.assertTrue(output.contains("zsh") || output.contains("compdef"));
    }

    @Test
    public void testBashCompletionContainsSourceInstruction() {
        GenerateCompletion.main(new String[]{"bash"});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("source <(rmqctl generate-completion bash)"));
    }

    @Test
    public void testZshCompletionContainsSourceInstruction() {
        GenerateCompletion.main(new String[]{"zsh"});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("source <(rmqctl generate-completion zsh)"));
    }

    @Test
    public void testCompletionScriptContainsSubcommands() {
        GenerateCompletion.main(new String[]{"bash"});
        String output = capturedOut.toString();
        Assert.assertTrue(output.contains("topic") || output.contains("config") || output.contains("cluster"));
    }
}