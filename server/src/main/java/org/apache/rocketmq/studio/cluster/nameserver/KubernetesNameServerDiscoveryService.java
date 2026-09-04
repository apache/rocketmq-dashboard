/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.nameserver;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointPort;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@EnableConfigurationProperties(KubernetesNameServerDiscoveryProperties.class)
public class KubernetesNameServerDiscoveryService {

    private static final String SERVICE_NAME_LABEL = "kubernetes.io/service-name";

    private final KubernetesNameServerDiscoveryClientFactory clientFactory;
    private final KubernetesNameServerDiscoveryProperties properties;

    public KubernetesNameServerDiscoveryVO discover(DiscoverKubernetesNameServersDTO command) {
        if (command == null || !StringUtils.hasText(command.getNamespace())) {
            throw new BusinessException(400, "namespace is required");
        }
        if (!properties.isEnabled()) {
            throw new BusinessException(503, "Kubernetes NameServer discovery is disabled");
        }
        String namespace = command.getNamespace().trim();
        validateConfiguration();

        try (KubernetesNameServerDiscoveryClient client = clientFactory.create()) {
            List<Service> services = client.listServices(namespace);
            List<EndpointSlice> endpointSlices = client.listEndpointSlices(namespace);
            List<KubernetesNameServerCandidateVO> candidates = servicePortCandidates(
                    services, endpointSlices, namespace);
            if (candidates.isEmpty()) {
                candidates = serviceHintCandidates(services, endpointSlices, namespace);
            }
            if (candidates.isEmpty()) {
                candidates = endpointSliceCandidates(endpointSlices, namespace);
            }
            if (candidates.isEmpty() && properties.isPodFallbackEnabled()) {
                candidates = podCandidates(client.listPodsByComponent(namespace), namespace, "POD_LABEL",
                        pod -> true);
            }
            if (candidates.isEmpty() && properties.isPodFallbackEnabled()) {
                candidates = podCandidates(client.listRocketMqPods(namespace), namespace, "POD_LABEL",
                        this::hasNameServerRoleEvidence);
            }
            if (candidates.isEmpty() && properties.isPodFallbackEnabled()) {
                candidates = podCandidates(client.listPods(namespace), namespace, "POD_IMAGE",
                        pod -> hasImageHint(pod) && hasNameServerRoleEvidence(pod));
            }
            return KubernetesNameServerDiscoveryVO.builder()
                    .namespace(namespace)
                    .observedAt(LocalDateTime.now())
                    .candidates(limitAndDeduplicate(candidates))
                    .build();
        } catch (BusinessException exception) {
            throw exception;
        } catch (KubernetesClientException exception) {
            int status = exception.getCode() == 403 ? 403 : 503;
            String message = status == 403
                    ? "Kubernetes RBAC denied NameServer discovery in namespace " + namespace
                    : "Kubernetes API is unavailable for NameServer discovery";
            throw new BusinessException(status, message);
        } catch (RuntimeException exception) {
            log.warn("Kubernetes NameServer discovery failed in namespace {}: {}", namespace,
                    exception.getClass().getSimpleName());
            throw new BusinessException(503, "Kubernetes API is unavailable for NameServer discovery");
        }
    }

    private List<KubernetesNameServerCandidateVO> servicePortCandidates(List<Service> services,
                                                                         List<EndpointSlice> endpointSlices,
                                                                         String namespace) {
        return safe(services).stream()
                .filter(this::hasMetadata)
                .filter(service -> isExternalName(service)
                        || hasReadyEndpoints(endpointSlices, service.getMetadata().getName()))
                .flatMap(service -> safe(service.getSpec() == null ? null : service.getSpec().getPorts()).stream()
                        .filter(port -> Objects.equals(port.getPort(), properties.getPort()))
                        .map(port -> serviceCandidate(service, namespace, port.getPort(), "SERVICE_PORT",
                                isExternalName(service) ? "MEDIUM" : "HIGH")))
                .toList();
    }

    private List<KubernetesNameServerCandidateVO> serviceHintCandidates(List<Service> services,
                                                                         List<EndpointSlice> endpointSlices,
                                                                         String namespace) {
        return safe(services).stream()
                .filter(this::hasMetadata)
                .filter(service -> hasNameHint(service.getMetadata().getName())
                        || safeMap(service.getMetadata().getLabels()).values().stream().anyMatch(this::hasNameHint))
                .filter(service -> isExternalName(service)
                        || hasReadyEndpoints(endpointSlices, service.getMetadata().getName()))
                .flatMap(service -> hintedServicePorts(service).stream()
                        .map(port -> serviceCandidate(service, namespace, port, "SERVICE_HINT", "MEDIUM")))
                .toList();
    }

    private List<Integer> hintedServicePorts(Service service) {
        List<ServicePort> ports = safe(service.getSpec() == null ? null : service.getSpec().getPorts());
        List<Integer> named = ports.stream()
                .filter(port -> hasNameHint(port.getName()))
                .map(ServicePort::getPort)
                .filter(Objects::nonNull)
                .toList();
        if (!named.isEmpty()) {
            return named;
        }
        if (hasNameHint(service.getMetadata().getName()) && ports.size() == 1 && ports.get(0).getPort() != null) {
            return List.of(ports.get(0).getPort());
        }
        return List.of();
    }

    private KubernetesNameServerCandidateVO serviceCandidate(Service service, String namespace, int port,
                                                               String source, String confidence) {
        String serviceName = service.getMetadata().getName();
        String clusterDomain = properties.getClusterDomain().trim().replaceAll("^\\.+|\\.+$", "");
        String dnsName = serviceName + "." + namespace + ".svc"
                + (clusterDomain.isEmpty() ? "" : "." + clusterDomain);
        return candidate(namespace, serviceName, hostPort(dnsName, port), source, confidence, true);
    }

    private List<KubernetesNameServerCandidateVO> endpointSliceCandidates(List<EndpointSlice> slices,
                                                                           String namespace) {
        Map<EndpointGroup, SortedSet<String>> groupedAddresses = new LinkedHashMap<>();
        for (EndpointSlice slice : safe(slices)) {
            if (!hasMetadata(slice)) {
                continue;
            }
            String serviceName = safeMap(slice.getMetadata().getLabels()).get(SERVICE_NAME_LABEL);
            for (EndpointPort port : safe(slice.getPorts())) {
                if (port.getPort() == null
                        || !Objects.equals(port.getPort(), properties.getPort()) && !hasNameHint(port.getName())) {
                    continue;
                }
                SortedSet<String> addresses = safe(slice.getEndpoints()).stream()
                        .filter(this::isReady)
                        .flatMap(endpoint -> safe(endpoint.getAddresses()).stream())
                        .filter(StringUtils::hasText)
                        .map(address -> hostPort(address.trim(), port.getPort()))
                        .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
                if (addresses.isEmpty()) {
                    continue;
                }
                String resourceName = StringUtils.hasText(serviceName)
                        ? serviceName : slice.getMetadata().getName();
                String confidence = Objects.equals(port.getPort(), properties.getPort()) ? "MEDIUM" : "LOW";
                EndpointGroup group = new EndpointGroup(resourceName, port.getPort(), confidence);
                groupedAddresses.computeIfAbsent(group, ignored -> new TreeSet<>()).addAll(addresses);
            }
        }
        return groupedAddresses.entrySet().stream()
                .map(entry -> candidate(namespace, entry.getKey().resourceName(),
                        String.join(";", entry.getValue()), "ENDPOINT_SLICE",
                        entry.getKey().confidence(), false))
                .toList();
    }

    private boolean isReady(Endpoint endpoint) {
        return endpoint.getConditions() == null || !Boolean.FALSE.equals(endpoint.getConditions().getReady());
    }

    private boolean hasReadyEndpoints(List<EndpointSlice> slices, String serviceName) {
        return safe(slices).stream()
                .filter(this::hasMetadata)
                .filter(slice -> serviceName.equals(safeMap(slice.getMetadata().getLabels()).get(SERVICE_NAME_LABEL)))
                .flatMap(slice -> safe(slice.getEndpoints()).stream())
                .filter(this::isReady)
                .flatMap(endpoint -> safe(endpoint.getAddresses()).stream())
                .anyMatch(StringUtils::hasText);
    }

    private boolean isExternalName(Service service) {
        return service.getSpec() != null && "ExternalName".equals(service.getSpec().getType())
                && StringUtils.hasText(service.getSpec().getExternalName());
    }

    private List<KubernetesNameServerCandidateVO> podCandidates(List<Pod> pods, String namespace, String source,
                                                                 Predicate<Pod> predicate) {
        return safe(pods).stream()
                .filter(this::isReady)
                .filter(predicate)
                .map(pod -> candidate(namespace, pod.getMetadata().getName(),
                        hostPort(pod.getStatus().getPodIP(), properties.getPort()), source, "LOW", false))
                .toList();
    }

    private boolean isReady(Pod pod) {
        if (!hasMetadata(pod) || pod.getStatus() == null || !StringUtils.hasText(pod.getStatus().getPodIP())
                || !"Running".equalsIgnoreCase(pod.getStatus().getPhase())) {
            return false;
        }
        return safe(pod.getStatus().getConditions()).stream()
                .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private boolean hasNameServerRoleEvidence(Pod pod) {
        if (safeMap(pod.getMetadata().getLabels()).values().stream().anyMatch(this::hasNameHint)) {
            return true;
        }
        return safe(pod.getSpec() == null ? null : pod.getSpec().getContainers()).stream()
                .anyMatch(container -> hasNameHint(container.getName())
                        || safe(container.getPorts()).stream().map(ContainerPort::getContainerPort)
                        .anyMatch(port -> Objects.equals(port, properties.getPort()))
                        || Stream.concat(safe(container.getCommand()).stream(), safe(container.getArgs()).stream())
                        .anyMatch(this::hasNameHint));
    }

    private boolean hasImageHint(Pod pod) {
        return safe(pod.getSpec() == null ? null : pod.getSpec().getContainers()).stream()
                .map(Container::getImage)
                .anyMatch(this::hasImageHint);
    }

    private boolean hasNameHint(String value) {
        return containsAny(value, properties.getNameHints());
    }

    private boolean hasImageHint(String value) {
        return containsAny(value, properties.getImageHints());
    }

    private boolean containsAny(String value, Collection<String> hints) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return (hints == null ? List.<String>of() : hints).stream().filter(StringUtils::hasText)
                .map(hint -> hint.toLowerCase(Locale.ROOT)).anyMatch(normalized::contains);
    }

    private List<KubernetesNameServerCandidateVO> limitAndDeduplicate(
            List<KubernetesNameServerCandidateVO> candidates) {
        Map<String, KubernetesNameServerCandidateVO> unique = new LinkedHashMap<>();
        safe(candidates).stream()
                .sorted(Comparator.comparing(KubernetesNameServerCandidateVO::getNamesrvAddr)
                        .thenComparing(KubernetesNameServerCandidateVO::getResourceName))
                .forEach(candidate -> unique.putIfAbsent(candidate.getNamesrvAddr(), candidate));
        return unique.values().stream().limit(properties.getMaxCandidates()).toList();
    }

    private KubernetesNameServerCandidateVO candidate(String namespace, String resourceName, String namesrvAddr,
                                                        String source, String confidence, boolean stable) {
        return KubernetesNameServerCandidateVO.builder()
                .namespace(namespace)
                .resourceName(resourceName)
                .namesrvAddr(namesrvAddr)
                .source(source)
                .confidence(confidence)
                .stable(stable)
                .build();
    }

    private String hostPort(String host, int port) {
        String normalized = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return normalized + ":" + port;
    }

    private void validateConfiguration() {
        if (properties.getPort() < 1 || properties.getPort() > 65535) {
            throw new BusinessException(503, "Kubernetes NameServer discovery port is invalid");
        }
        if (properties.getMaxCandidates() < 1 || properties.getMaxCandidates() > 500) {
            throw new BusinessException(503, "Kubernetes NameServer discovery max-candidates must be 1-500");
        }
    }

    private boolean hasMetadata(Service service) {
        return service != null && service.getMetadata() != null
                && StringUtils.hasText(service.getMetadata().getName());
    }

    private boolean hasMetadata(EndpointSlice slice) {
        return slice != null && slice.getMetadata() != null
                && StringUtils.hasText(slice.getMetadata().getName());
    }

    private boolean hasMetadata(Pod pod) {
        return pod != null && pod.getMetadata() != null && StringUtils.hasText(pod.getMetadata().getName());
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, String> safeMap(Map<String, String> values) {
        return values == null ? Map.of() : values;
    }

    private record EndpointGroup(String resourceName, int port, String confidence) {
    }
}
