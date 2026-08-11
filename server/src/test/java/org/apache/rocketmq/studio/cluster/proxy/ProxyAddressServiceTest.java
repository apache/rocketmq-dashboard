/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.proxy;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyAddressServiceTest {
    private InMemoryRepository repository;
    private ProxyAddressService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        service = new ProxyAddressService(repository, new RestTemplate());
    }

    @Test
    void homePageShouldInitializeAndPersistDefaultProxyAddress() {
        assertThat(service.getHomePage().getProxyAddrList())
                .containsExactly(ProxyAddressService.DEFAULT_PROXY_ADDRESS);

        ProxyAddressService restarted = new ProxyAddressService(repository, new RestTemplate());
        assertThat(restarted.getHomePage().getCurrentProxyAddr())
                .isEqualTo(ProxyAddressService.DEFAULT_PROXY_ADDRESS);
    }

    @Test
    void addressesShouldRemainIsolatedByClusterScope() {
        service.addProxyAddr("cluster-a", "10.0.0.1:8081");
        service.addProxyAddr("cluster-b", "10.0.0.2:8081");

        assertThat(service.getHomePage("cluster-a").getProxyAddrList())
                .containsExactly("10.0.0.1:8081");
        assertThat(service.getHomePage("cluster-b").getProxyAddrList())
                .containsExactly("10.0.0.2:8081");
    }

    @Test
    void addProxyAddrShouldTrimAndKeepUniqueAddresses() {
        service.addProxyAddr(" 10.0.0.1:8081 ");
        service.addProxyAddr("10.0.0.1:8081");

        assertThat(service.getHomePage().getProxyAddrList()).containsExactly("10.0.0.1:8081");
        assertThat(service.getHomePage().getCurrentProxyAddr()).isEqualTo("10.0.0.1:8081");
    }

    @Test
    void addProxyAddrShouldAcceptBracketedIpv6Address() {
        service.addProxyAddr(" [::1]:8081 ");
        assertThat(service.getHomePage().getProxyAddrList()).containsExactly("[::1]:8081");
    }

    @Test
    void addProxyAddrShouldRejectInvalidAddressFormats() {
        List<String> invalid = List.of("10.0.0.1", "10.0.0.1:abc", "10.0.0.1:0",
                "10.0.0.1:65536", "http://10.0.0.1:8081", "10.0.0.1:8081/path");
        for (String address : invalid) {
            assertThatThrownBy(() -> service.addProxyAddr(address))
                    .as(address).isInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        }
    }

    @Test
    void removingSelectedAddressShouldSelectNextPersistedAddress() {
        service.addProxyAddr("cluster-a", "10.0.0.1:8081");
        service.addProxyAddr("cluster-a", "10.0.0.2:8081");
        service.removeProxyAddr("cluster-a", "10.0.0.1:8081");

        ProxyHomeVO home = service.getHomePage("cluster-a");
        assertThat(home.getProxyAddrList()).containsExactly("10.0.0.2:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("10.0.0.2:8081");
    }

    @Test
    void removeProxyAddrShouldRejectUnknownAddress() {
        assertThatThrownBy(() -> service.removeProxyAddr("10.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy address not found: 10.0.0.1:8081")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));
    }

    @Test
    void reloadConfigShouldRejectAddressRegisteredInAnotherScope() {
        service.addProxyAddr("cluster-a", "10.0.0.1:8081");

        assertThatThrownBy(() -> service.reloadConfig("cluster-b", "10.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("addr is not a registered proxy address");
    }

    private static final class InMemoryRepository implements ProxyAddressRepository {
        private final Map<String, LinkedHashMap<String, ProxyAddressRecord>> scopes = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public List<ProxyAddressRecord> findByScope(String scopeId) {
            return new ArrayList<>(scopes.getOrDefault(scopeId, new LinkedHashMap<>()).values());
        }

        @Override
        public boolean insert(String scopeId, String address, boolean selected) {
            LinkedHashMap<String, ProxyAddressRecord> records =
                    scopes.computeIfAbsent(scopeId, ignored -> new LinkedHashMap<>());
            if (records.containsKey(address)) return false;
            LocalDateTime now = LocalDateTime.now();
            records.put(address, ProxyAddressRecord.builder().id(nextId++).scopeId(scopeId)
                    .address(address).selected(selected).createdAt(now).updatedAt(now).build());
            return true;
        }

        @Override
        public boolean delete(String scopeId, String address) {
            LinkedHashMap<String, ProxyAddressRecord> records = scopes.get(scopeId);
            return records != null && records.remove(address) != null;
        }

        @Override
        public boolean select(String scopeId, String address) {
            LinkedHashMap<String, ProxyAddressRecord> records = scopes.get(scopeId);
            if (records == null || !records.containsKey(address)) return false;
            records.values().forEach(record -> record.setSelected(address.equals(record.getAddress())));
            return true;
        }
    }
}
