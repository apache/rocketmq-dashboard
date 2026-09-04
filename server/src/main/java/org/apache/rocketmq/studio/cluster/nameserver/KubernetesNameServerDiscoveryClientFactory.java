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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KubernetesNameServerDiscoveryClientFactory {

    KubernetesNameServerDiscoveryClient create() {
        return new Fabric8Client(new KubernetesClientBuilder().build());
    }

    private record Fabric8Client(KubernetesClient client) implements KubernetesNameServerDiscoveryClient {

        @Override
        public List<Service> listServices(String namespace) {
            return client.services().inNamespace(namespace).list().getItems();
        }

        @Override
        public List<EndpointSlice> listEndpointSlices(String namespace) {
            return client.discovery().v1().endpointSlices().inNamespace(namespace).list().getItems();
        }

        @Override
        public List<Pod> listPodsByComponent(String namespace) {
            return client.pods().inNamespace(namespace)
                    .withLabelIn("app.kubernetes.io/component", "nameserver", "namesrv")
                    .list().getItems();
        }

        @Override
        public List<Pod> listRocketMqPods(String namespace) {
            return client.pods().inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", "rocketmq")
                    .list().getItems();
        }

        @Override
        public List<Pod> listPods(String namespace) {
            return client.pods().inNamespace(namespace).list().getItems();
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
