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
package org.apache.rocketmq.studio.cluster.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MultiBackendMetricsSourceTest {

    private HttpServer server;
    private String baseUrl;
    private final MetricsSourceFactory factory =
            new MetricsSourceFactory(RestClient.builder(), new ObjectMapper());

    private static ProxySelector originalProxySelector;

    @BeforeAll
    static void bypassJvmProxy() {
        // The IDE (e.g. IDEA with a PAC proxy) may inject a proxy into the test JVM. The embedded
        // server is bound to a site-local address that is not in http.nonProxyHosts, so the request
        // would be routed through the proxy and time out. Force a direct connection for this test.
        originalProxySelector = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
                // Nothing to do; the test never relies on a proxy.
            }
        });
    }

    @AfterAll
    static void restoreJvmProxy() {
        ProxySelector.setDefault(originalProxySelector);
    }

    @BeforeEach
    void setUp() throws IOException {
        java.net.InetAddress bindAddress = findSiteLocalAddress();
        server = HttpServer.create(new InetSocketAddress(bindAddress, 0), 0);
        baseUrl = "http://" + bindAddress.getHostAddress() + ":" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @ParameterizedTest
    @EnumSource(MetricsBackendType.class)
    void everyBackendShouldHitItsOwnQueryPathAndParseTheResponse(MetricsBackendType backendType) {
        AtomicReference<String> requestPath = new AtomicReference<>();
        server.createContext(backendType.getQueryPath(), exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, """
                    {"status":"success","data":{"resultType":"matrix","result":[
                      {"metric":{"backend":"%s"},"values":[[1784107658,"1.0"]]}
                    ]}}
                    """.formatted(backendType.name()));
        });

        MetricsDataSourceConfig config = configFor(backendType);
        MetricsSource source = factory.create(config);
        MetricDataVO result = source.query(query());

        assertThat(requestPath.get()).isEqualTo(backendType.getQueryPath());
        assertThat(result.getResultType()).isEqualTo("matrix");
        assertThat(result.getSeries()).hasSize(1);
        assertThat(result.getSeries().get(0).getLabels()).containsEntry("backend", backendType.name());
        assertThat(result.getSeries().get(0).getValues().get(0).getValue()).isEqualTo("1.0");
    }

    @Test
    void factoryShouldReturnTheMatchingConcreteClassPerProviderType() {
        assertThat(factory.create(configFor(MetricsBackendType.PROMETHEUS)))
                .isInstanceOf(PrometheusMetricsSource.class);
        assertThat(factory.create(configFor(MetricsBackendType.VICTORIA_METRICS)))
                .isInstanceOf(VictoriaMetricsMetricsSource.class);
        assertThat(factory.create(configFor(MetricsBackendType.THANOS)))
                .isInstanceOf(ThanosMetricsSource.class);
        assertThat(factory.create(configFor(MetricsBackendType.CORTEX)))
                .isInstanceOf(CortexMetricsSource.class);
        assertThat(factory.create(configFor(MetricsBackendType.MIMIR)))
                .isInstanceOf(MimirMetricsSource.class);
        assertThat(factory.create(configFor(MetricsBackendType.ARMS)))
                .isInstanceOf(ArmsMetricsSource.class);
    }

    @Test
    void unknownProviderTypeShouldFallBackToPrometheus() {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setProviderType("does-not-exist");
        config.setUrl(baseUrl);
        assertThat(factory.create(config)).isInstanceOf(PrometheusMetricsSource.class);
    }

    private MetricsDataSourceConfig configFor(MetricsBackendType backendType) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName(backendType.name().toLowerCase());
        config.setProviderType(backendType.name());
        config.setUrl(baseUrl);
        return config;
    }

    private MetricQueryDTO query() {
        return MetricQueryDTO.builder()
                .metric("up")
                .start(1784107658L)
                .end(1784108558L)
                .step("30s")
                .build();
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static java.net.InetAddress findSiteLocalAddress() throws java.net.SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback()) {
                continue;
            }
            for (InterfaceAddress address : iface.getInterfaceAddresses()) {
                InetAddress inet = address.getAddress();
                if (inet instanceof java.net.Inet4Address
                        && inet.isSiteLocalAddress()
                        && !inet.isLoopbackAddress()
                        && !inet.isLinkLocalAddress()) {
                    return inet;
                }
            }
        }
        return java.net.InetAddress.getLoopbackAddress();
    }
}
