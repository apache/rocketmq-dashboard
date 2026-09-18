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

package org.apache.rocketmq.studio.ops;

import org.apache.rocketmq.remoting.netty.TlsSystemConfig;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpsRuntimeConnectionTest {

    @TempDir
    private Path tempDir;

    @Test
    void currentShouldUseExternalDefaultsWithoutTouchingRepositoryWhenRuntimeIsDisabled() {
        OpsRuntimeProperties properties = properties(false, "ns1:9876;NS2:9876");
        OpsRuntimeConnection runtimeConnection = new OpsRuntimeConnection(new FailingRepository(), properties, () -> true);

        OpsConnectionSettings settings = runtimeConnection.current();

        assertThat(settings.addresses()).containsExactly("ns1:9876", "ns2:9876");
        assertThat(settings.currentNamesrv()).isEqualTo("ns1:9876");
        assertThat(settings.useVIPChannel()).isFalse();
        assertThat(settings.useTLS()).isFalse();
    }

    @Test
    void secondRuntimeConnectionShouldSeeSavedSettingsOnNextCurrentCall() {
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "ns1:9876");
        OpsRuntimeConnection first = new OpsRuntimeConnection(repository, properties, () -> true);
        OpsRuntimeConnection second = new OpsRuntimeConnection(repository, properties, () -> true);

        first.update(settings -> new OpsConnectionSettings(
                List.of(settings.currentNamesrv(), "ns2:9876"), "ns2:9876", true, true));

        OpsConnectionSettings loaded = second.current();
        assertThat(loaded.addresses()).containsExactly("ns1:9876", "ns2:9876");
        assertThat(loaded.currentNamesrv()).isEqualTo("ns2:9876");
        assertThat(loaded.useVIPChannel()).isTrue();
        assertThat(loaded.useTLS()).isTrue();
    }

    @Test
    void updateShouldRejectMoreThanTheConfiguredAddressLimitBeforePersisting() {
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "ns1:9876");
        properties.setMaxNameServerAddresses(1);
        OpsRuntimeConnection runtimeConnection = new OpsRuntimeConnection(repository, properties, () -> true);

        assertThatThrownBy(() -> runtimeConnection.update(settings -> new OpsConnectionSettings(
                List.of("ns1:9876", "ns2:9876"), "ns1:9876", false, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr list exceeds the limit of 1");

        assertThat(repository.load()).isEmpty();
    }

    @Test
    void enabledCurrentShouldFailClosedWhenRepositoryIsUnavailable() {
        OpsRuntimeProperties properties = properties(true, "ns1:9876");
        OpsRuntimeConnection runtimeConnection = new OpsRuntimeConnection(new FailingRepository(), properties, () -> true);

        assertThatThrownBy(runtimeConnection::current)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops runtime settings storage is unavailable")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(503));
    }

    @Test
    void updateShouldFailClosedWhenSaveFailsAndLeaveEffectiveSettingsUnchanged() {
        SaveFailingRepository repository = new SaveFailingRepository(
                new OpsConnectionSettings(List.of("ns1:9876"), "ns1:9876", false, false));
        OpsRuntimeProperties properties = properties(true, "ignored:9876");
        OpsRuntimeConnection runtimeConnection = new OpsRuntimeConnection(repository, properties, () -> true);

        assertThatThrownBy(() -> runtimeConnection.update(settings -> new OpsConnectionSettings(
                List.of("ns1:9876", "ns2:9876"), "ns2:9876", false, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops runtime settings storage is unavailable");

        OpsConnectionSettings loaded = repository.load().orElseThrow();
        assertThat(loaded.addresses()).containsExactly("ns1:9876");
        assertThat(loaded.currentNamesrv()).isEqualTo("ns1:9876");
    }

    @Test
    void tlsShouldRequireSecureDeploymentSettingsBeforePersisting() {
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "ns1:9876");
        OpsRuntimeConnection runtimeConnection = new OpsRuntimeConnection(repository, properties, () -> false);

        assertThatThrownBy(() -> runtimeConnection.update(settings -> new OpsConnectionSettings(
                settings.addresses(), settings.currentNamesrv(), false, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TLS requires");

        assertThat(repository.load()).isEmpty();
    }

    @Test
    void tlsShouldRequireReadableTrustCertPathBeforePersisting() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "ns1:9876");
        OpsRuntimeConnection runtimeConnection = new OpsRuntimeConnection(repository, properties);
        boolean originalTestMode = TlsSystemConfig.tlsTestModeEnable;
        boolean originalAuthServer = TlsSystemConfig.tlsClientAuthServer;
        String originalTrustCertPath = TlsSystemConfig.tlsClientTrustCertPath;
        try {
            TlsSystemConfig.tlsTestModeEnable = false;
            TlsSystemConfig.tlsClientAuthServer = true;
            TlsSystemConfig.tlsClientTrustCertPath = tempDir.resolve("missing-ca.pem").toString();

            assertThatThrownBy(() -> runtimeConnection.update(settings -> new OpsConnectionSettings(
                    settings.addresses(), settings.currentNamesrv(), false, true)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tls.client.trustCertPath");

            Path readableTrustCert = tempDir.resolve("ca.pem");
            Files.writeString(readableTrustCert, "test-ca");
            TlsSystemConfig.tlsClientTrustCertPath = readableTrustCert.toString();

            runtimeConnection.update(settings -> new OpsConnectionSettings(
                    settings.addresses(), settings.currentNamesrv(), false, true));

            assertThat(repository.load().orElseThrow().useTLS()).isTrue();
        } finally {
            TlsSystemConfig.tlsTestModeEnable = originalTestMode;
            TlsSystemConfig.tlsClientAuthServer = originalAuthServer;
            TlsSystemConfig.tlsClientTrustCertPath = originalTrustCertPath;
        }
    }

    private static OpsRuntimeProperties properties(boolean enabled, String namesrvAddr) {
        OpsRuntimeProperties properties = new OpsRuntimeProperties();
        properties.setEnabled(enabled);
        properties.setNamesrvAddr(namesrvAddr);
        return properties;
    }

    private static final class InMemoryRepository implements OpsConnectionRepository {
        private OpsConnectionSettings settings;

        @Override
        public Optional<OpsConnectionSettings> load() {
            return Optional.ofNullable(settings);
        }

        @Override
        public void save(OpsConnectionSettings settings) {
            this.settings = settings;
        }
    }

    private static final class FailingRepository implements OpsConnectionRepository {
        @Override
        public Optional<OpsConnectionSettings> load() {
            throw new DataAccessResourceFailureException("db down");
        }

        @Override
        public void save(OpsConnectionSettings settings) {
            throw new DataAccessResourceFailureException("db down");
        }
    }

    private static final class SaveFailingRepository implements OpsConnectionRepository {
        private final OpsConnectionSettings settings;

        private SaveFailingRepository(OpsConnectionSettings settings) {
            this.settings = settings;
        }

        @Override
        public Optional<OpsConnectionSettings> load() {
            return Optional.of(settings);
        }

        @Override
        public void save(OpsConnectionSettings settings) {
            throw new DataAccessResourceFailureException("db down");
        }
    }
}
