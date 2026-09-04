/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.nameserver;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;

import java.util.List;

interface KubernetesNameServerDiscoveryClient extends AutoCloseable {
    List<Service> listServices(String namespace);

    List<EndpointSlice> listEndpointSlices(String namespace);

    List<Pod> listPodsByComponent(String namespace);

    List<Pod> listRocketMqPods(String namespace);

    List<Pod> listPods(String namespace);

    @Override
    void close();
}
