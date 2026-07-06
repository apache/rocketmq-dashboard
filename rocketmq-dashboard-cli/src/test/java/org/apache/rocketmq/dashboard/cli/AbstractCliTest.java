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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.BeforeClass;

/**
 * Base test class that manages a shared temp home directory for all CliContext-based tests.
 * CliContext has static final Path fields initialized at class-load time, so we must
 * set user.home BEFORE CliContext is loaded and keep the same directory for the entire
 * test run. The directory is created once and never reset during the run.
 *
 * Subclasses should call {@link #resetConfig()} in their own {@code @Before} methods.
 */
public abstract class AbstractCliTest {

    private static Path tempHomeDir;
    private static String originalUserHome;
    private static boolean initialized = false;

    @BeforeClass
    public static synchronized void setUpClass() throws IOException {
        if (!initialized) {
            initialized = true;
            originalUserHome = System.getProperty("user.home");
            tempHomeDir = Files.createTempDirectory("rmqctl-test-home");
            System.setProperty("user.home", tempHomeDir.toString());
        }
    }

    /**
     * Write a clean empty config file so CliContext.loadConfig() reads valid YAML.
     * Call this from subclass @Before methods before each test.
     */
    protected static void resetConfig() throws IOException {
        if (tempHomeDir == null) {
            throw new IllegalStateException("tempHomeDir not initialized. Ensure setUpClass runs first.");
        }
        Path rqmctlDir = tempHomeDir.resolve(".rmqctl");
        Files.createDirectories(rqmctlDir);

        // Write a valid empty CliConfig YAML. SnakeYAML Constructor(CliConfig.class)
        // can parse this without hitting the global-tag ComposerException.
        Path configFile = rqmctlDir.resolve("config.yaml");
        // Use an empty document structure that maps cleanly to CliConfig
        String emptyConfig = "clusters: {}\nusers: {}\ncontexts: []\n";
        Files.write(configFile, emptyConfig.getBytes());
    }

    /**
     * Write a custom clean YAML config for CliContext to load.
     */
    protected static void writeConfig(String yamlContent) throws IOException {
        if (tempHomeDir == null) {
            throw new IllegalStateException("tempHomeDir not initialized");
        }
        Path rqmctlDir = tempHomeDir.resolve(".rmqctl");
        Files.createDirectories(rqmctlDir);
        Path configFile = rqmctlDir.resolve("config.yaml");
        Files.write(configFile, yamlContent.getBytes());
    }

    protected static Path getTempHomeDir() {
        return tempHomeDir;
    }

    /**
     * Wire up the parent chain for a subcommand so that {@code parent.root} works.
     * In production, picocli injects @ParentCommand fields automatically; in unit tests
     * we create instances directly, so we must set them manually.
     *
     * @param subcmd  the subcommand instance (e.g. ClusterCommand.DescribeCmd)
     * @param parent  the parent command instance (e.g. new ClusterCommand())
     * @param <P>     parent command type
     * @param <C>     subcommand type
     * @return the subcmd with its parent field set
     */
    @SuppressWarnings("unchecked")
    protected static <P, C> C withParent(C subcmd, P parent) {
        try {
            java.lang.reflect.Field parentField = subcmd.getClass().getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(subcmd, parent);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set parent field on " + subcmd.getClass().getName(), e);
        }
        return subcmd;
    }

    /**
     * Create a parent command with its root (RmqctlCommand) wired up.
     * The parent's {@code root} field is set to a new RmqctlCommand instance.
     *
     * @param parentClass the parent command class (e.g. ClusterCommand.class)
     * @param <P>         parent command type
     * @return a new parent instance with root set
     */
    protected static <P> P newParentWithRoot(Class<P> parentClass) {
        try {
            P parent = parentClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Field rootField = parentClass.getDeclaredField("root");
            rootField.setAccessible(true);
            rootField.set(parent, new RmqctlCommand());
            return parent;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create parent with root: " + parentClass.getName(), e);
        }
    }
}
