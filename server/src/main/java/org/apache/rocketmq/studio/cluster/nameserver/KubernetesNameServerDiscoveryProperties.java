/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.nameserver;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "studio.kubernetes.nameserver-discovery")
public class KubernetesNameServerDiscoveryProperties {
    private boolean enabled;
    private int port = 9876;
    private String clusterDomain = "cluster.local";
    private boolean podFallbackEnabled = true;
    private int maxCandidates = 50;
    private List<String> nameHints = List.of("mqnamesrv", "nameserver", "namesrv");
    private List<String> imageHints = List.of("rocketmq");
}
