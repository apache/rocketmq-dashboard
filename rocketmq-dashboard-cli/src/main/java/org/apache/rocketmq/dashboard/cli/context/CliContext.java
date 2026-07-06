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
package org.apache.rocketmq.dashboard.cli.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/** Manages CLI context configuration persisted to ~/.rmqctl/config.yaml. Provides kubectl-style context switching and cluster registration. */
public class CliContext {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".rmqctl");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.yaml");

    private final Yaml yaml;
    private CliConfig config;

    public CliContext() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        representer.addClassTag(CliConfig.class, Tag.MAP);
        LoaderOptions loaderOptions = new LoaderOptions();
        TypeDescription clientConfigDesc = new TypeDescription(CliConfig.class,
                "tag:yaml.org,2002:org.apache.rocketmq.dashboard.cli.context.CliConfig");
        Constructor constructor = new Constructor(clientConfigDesc, loaderOptions);
        this.yaml = new Yaml(constructor, representer, dumperOptions);
        loadConfig();
    }

    private void loadConfig() {
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                config = yaml.loadAs(in, CliConfig.class);
                if (config == null) {
                    config = new CliConfig();
                }
            } catch (IOException e) {
                config = new CliConfig();
            }
        } else {
            config = new CliConfig();
        }
    }

    public void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                yaml.dump(config, new java.io.OutputStreamWriter(out));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config to " + CONFIG_FILE, e);
        }
    }

    public List<CliConfig.ContextEntry> getContexts() {
        if (config.getContexts() == null) {
            return Collections.emptyList();
        }
        return config.getContexts();
    }

    public String getCurrentContext() {
        return config.getCurrentContext();
    }

    public void setCurrentContext(String name) {
        config.setCurrentContext(name);
        saveConfig();
    }

    public CliConfig.ContextEntry resolveCurrentContext() {
        String current = getCurrentContext();
        if (current == null) {
            return null;
        }
        for (CliConfig.ContextEntry entry : getContexts()) {
            if (current.equals(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    public CliConfig.ClusterEntry resolveCluster() {
        CliConfig.ContextEntry ctx = resolveCurrentContext();
        if (ctx == null) {
            return null;
        }
        return config.getClusters().get(ctx.getCluster());
    }

    public void addContext(String name, String cluster, String user, String namespace) {
        CliConfig.ContextEntry existing = null;
        for (CliConfig.ContextEntry entry : getContexts()) {
            if (name.equals(entry.getName())) {
                existing = entry;
                break;
            }
        }
        if (existing != null) {
            existing.setCluster(cluster);
            existing.setUser(user);
            existing.setNamespace(namespace);
        } else {
            CliConfig.ContextEntry entry = new CliConfig.ContextEntry();
            entry.setName(name);
            entry.setCluster(cluster);
            entry.setUser(user);
            entry.setNamespace(namespace);
            config.getContexts().add(entry);
        }
        saveConfig();
    }

    public void addCluster(String name, String namesrvAddr, String proxyAddr, String clusterType) {
        CliConfig.ClusterEntry entry = new CliConfig.ClusterEntry();
        entry.setName(name);
        entry.setNamesrvAddr(namesrvAddr);
        entry.setProxyAddr(proxyAddr);
        entry.setClusterType(clusterType);
        config.getClusters().put(name, entry);
        saveConfig();
    }

    public Set<String> getClusterNames() {
        return config.getClusters().keySet();
    }

    public CliConfig getConfig() {
        return config;
    }
}
