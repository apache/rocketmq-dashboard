/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.nameserver;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KubernetesNameServerDiscoveryServiceTest {

    private KubernetesNameServerDiscoveryClientFactory clientFactory;
    private KubernetesNameServerDiscoveryProperties properties;

    @BeforeEach
    void setUp() {
        clientFactory = mock(KubernetesNameServerDiscoveryClientFactory.class);
        properties = new KubernetesNameServerDiscoveryProperties();
        properties.setEnabled(true);
    }

    @Test
    void servicePortMatchStopsLowerCostFallbacksTest() {
        FakeClient client = new FakeClient();
        client.services = List.of(service("rmq-nameserver", 9876, null));
        client.endpointSlices = List.of(endpointSlice("rmq-nameserver-a", "10.0.0.10", null, true));
        when(clientFactory.create()).thenReturn(client);

        KubernetesNameServerDiscoveryVO result = service().discover(request());

        assertThat(result.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getNamesrvAddr())
                    .isEqualTo("rmq-nameserver.mq.svc.cluster.local:9876");
            assertThat(candidate.getSource()).isEqualTo("SERVICE_PORT");
            assertThat(candidate.getConfidence()).isEqualTo("HIGH");
            assertThat(candidate.isStable()).isTrue();
        });
        assertThat(client.serviceCalls).isEqualTo(1);
        assertThat(client.endpointSliceCalls).isEqualTo(1);
        assertThat(client.podCalls).isZero();
        assertThat(client.closed).isTrue();
    }

    @Test
    void serviceNameHintUsesTheSingleExposedPortTest() {
        FakeClient client = new FakeClient();
        client.services = List.of(service("custom-namesrv", 19876, null));
        client.endpointSlices = List.of(endpointSlice("custom-namesrv-a", "10.0.0.10", null, true,
                "custom-namesrv"));
        when(clientFactory.create()).thenReturn(client);

        KubernetesNameServerCandidateVO candidate = service().discover(request()).getCandidates().get(0);

        assertThat(candidate.getNamesrvAddr()).isEqualTo("custom-namesrv.mq.svc.cluster.local:19876");
        assertThat(candidate.getSource()).isEqualTo("SERVICE_HINT");
        assertThat(client.endpointSliceCalls).isEqualTo(1);
    }

    @Test
    void serviceWithoutReadyEndpointsFallsBackInsteadOfReportingHighConfidenceTest() {
        FakeClient client = new FakeClient();
        client.services = List.of(service("stale-nameserver", 9876, null));
        client.componentPods = List.of(nameserverPod("rmq-namesrv-0", "10.0.0.20", "apache/rocketmq:5"));
        when(clientFactory.create()).thenReturn(client);

        KubernetesNameServerCandidateVO candidate = service().discover(request()).getCandidates().get(0);

        assertThat(candidate.getSource()).isEqualTo("POD_LABEL");
        assertThat(candidate.getNamesrvAddr()).isEqualTo("10.0.0.20:9876");
    }

    @Test
    void endpointSliceFallbackReturnsOnlyReadyAddressesTest() {
        FakeClient client = new FakeClient();
        client.endpointSlices = List.of(endpointSlice("rmq-nameserver-a", "2001:db8::10", "10.0.0.10", true),
                endpointSlice("rmq-nameserver-b", "10.0.0.12", null, true),
                endpointSlice("rmq-nameserver-c", "10.0.0.11", null, false));
        when(clientFactory.create()).thenReturn(client);

        KubernetesNameServerCandidateVO candidate = service().discover(request()).getCandidates().get(0);

        assertThat(candidate.getResourceName()).isEqualTo("rmq-nameserver");
        assertThat(candidate.getNamesrvAddr())
                .isEqualTo("10.0.0.10:9876;10.0.0.12:9876;[2001:db8::10]:9876");
        assertThat(candidate.getSource()).isEqualTo("ENDPOINT_SLICE");
        assertThat(candidate.isStable()).isFalse();
        assertThat(client.endpointSliceCalls).isEqualTo(1);
        assertThat(client.podCalls).isZero();
    }

    @Test
    void componentLabelPodFallbackPrecedesNamespaceImageScanTest() {
        FakeClient client = new FakeClient();
        client.componentPods = List.of(nameserverPod("rmq-namesrv-0", "10.0.0.20", "apache/rocketmq:5"));
        when(clientFactory.create()).thenReturn(client);

        KubernetesNameServerCandidateVO candidate = service().discover(request()).getCandidates().get(0);

        assertThat(candidate.getNamesrvAddr()).isEqualTo("10.0.0.20:9876");
        assertThat(candidate.getSource()).isEqualTo("POD_LABEL");
        assertThat(client.componentPodCalls).isEqualTo(1);
        assertThat(client.rocketMqPodCalls).isZero();
        assertThat(client.allPodCalls).isZero();
    }

    @Test
    void fullPodImageScanRequiresNameServerRoleEvidenceTest() {
        FakeClient client = new FakeClient();
        client.pods = List.of(
                nameserverPod("rmq-namesrv-0", "10.0.0.30", "apache/rocketmq:5"),
                brokerPod("rmq-broker-0", "10.0.0.31", "apache/rocketmq:5"));
        when(clientFactory.create()).thenReturn(client);

        List<KubernetesNameServerCandidateVO> candidates = service().discover(request()).getCandidates();

        assertThat(candidates).singleElement()
                .extracting(KubernetesNameServerCandidateVO::getResourceName,
                        KubernetesNameServerCandidateVO::getSource)
                .containsExactly("rmq-namesrv-0", "POD_IMAGE");
        assertThat(client.allPodCalls).isEqualTo(1);
    }

    @Test
    void disabledDiscoveryDoesNotCreateKubernetesClientTest() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service().discover(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Kubernetes NameServer discovery is disabled")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(503));
    }

    @Test
    void serviceBoundaryRequiresNamespaceTest() {
        assertThatThrownBy(() -> service().discover(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namespace is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
    }

    @Test
    void rbacFailureIsExplicitAndDoesNotExposeUpstreamDetailsTest() {
        KubernetesNameServerDiscoveryClient client = mock(KubernetesNameServerDiscoveryClient.class);
        when(client.listServices("mq")).thenThrow(new KubernetesClientException("token=secret", 403, null));
        when(clientFactory.create()).thenReturn(client);

        assertThatThrownBy(() -> service().discover(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Kubernetes RBAC denied NameServer discovery in namespace mq")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403));
    }

    private KubernetesNameServerDiscoveryService service() {
        return new KubernetesNameServerDiscoveryService(clientFactory, properties);
    }

    private DiscoverKubernetesNameServersDTO request() {
        return DiscoverKubernetesNameServersDTO.builder().namespace("mq").build();
    }

    private Service service(String name, int port, String portName) {
        return new ServiceBuilder()
                .withNewMetadata().withName(name).withNamespace("mq").endMetadata()
                .withNewSpec()
                .addNewPort().withName(portName).withPort(port).endPort()
                .endSpec()
                .build();
    }

    private EndpointSlice endpointSlice(String sliceName, String firstAddress, String secondAddress,
                                         boolean ready) {
        return endpointSlice(sliceName, firstAddress, secondAddress, ready, "rmq-nameserver");
    }

    private EndpointSlice endpointSlice(String sliceName, String firstAddress, String secondAddress,
                                         boolean ready, String serviceName) {
        EndpointSliceBuilder builder = new EndpointSliceBuilder()
                .withNewMetadata()
                .withName(sliceName)
                .withNamespace("mq")
                .addToLabels("kubernetes.io/service-name", serviceName)
                .endMetadata()
                .addNewPort().withName("namesrv").withPort(9876).endPort()
                .addNewEndpoint()
                .withAddresses(firstAddress)
                .withNewConditions().withReady(ready).endConditions()
                .endEndpoint();
        if (secondAddress != null) {
            builder.addNewEndpoint()
                    .withAddresses(secondAddress)
                    .withNewConditions().withReady(ready).endConditions()
                    .endEndpoint();
        }
        return builder.build();
    }

    private Pod nameserverPod(String name, String ip, String image) {
        return readyPod(name, ip, image, "mqnamesrv", 9876);
    }

    private Pod brokerPod(String name, String ip, String image) {
        return readyPod(name, ip, image, "broker", 10911);
    }

    private Pod readyPod(String name, String ip, String image, String command, int port) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace("mq").endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(image)
                .withCommand(command)
                .addNewPort().withContainerPort(port).endPort()
                .endContainer()
                .endSpec()
                .withNewStatus()
                .withPhase("Running")
                .withPodIP(ip)
                .addNewCondition().withType("Ready").withStatus("True").endCondition()
                .endStatus()
                .build();
    }

    private static class FakeClient implements KubernetesNameServerDiscoveryClient {
        private List<Service> services = new ArrayList<>();
        private List<EndpointSlice> endpointSlices = new ArrayList<>();
        private List<Pod> componentPods = new ArrayList<>();
        private List<Pod> rocketMqPods = new ArrayList<>();
        private List<Pod> pods = new ArrayList<>();
        private int serviceCalls;
        private int endpointSliceCalls;
        private int componentPodCalls;
        private int rocketMqPodCalls;
        private int allPodCalls;
        private int podCalls;
        private boolean closed;

        @Override
        public List<Service> listServices(String namespace) {
            serviceCalls++;
            return services;
        }

        @Override
        public List<EndpointSlice> listEndpointSlices(String namespace) {
            endpointSliceCalls++;
            return endpointSlices;
        }

        @Override
        public List<Pod> listPodsByComponent(String namespace) {
            componentPodCalls++;
            podCalls++;
            return componentPods;
        }

        @Override
        public List<Pod> listRocketMqPods(String namespace) {
            rocketMqPodCalls++;
            podCalls++;
            return rocketMqPods;
        }

        @Override
        public List<Pod> listPods(String namespace) {
            allPodCalls++;
            podCalls++;
            return pods;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
