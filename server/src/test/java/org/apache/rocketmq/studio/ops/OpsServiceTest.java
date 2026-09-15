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

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpsServiceTest {

    @AfterEach
    void clearAuthenticatedUser() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void getHomePageShouldReportUnavailableConfigurationWhenRuntimeIsDisabled() {
        OpsService opsService = service(runtime(false, "10.0.0.1:9876", new InMemoryRepository(), true),
                properties(false, "10.0.0.1:9876"));

        OpsHomeVO home = opsService.getHomePage();

        assertThat(home.isConfigurationAvailable()).isFalse();
        assertThat(home.getNamesvrAddrList()).isEmpty();
        assertThat(home.getCurrentNamesrv()).isEmpty();
        assertThat(home.isUseVIPChannel()).isFalse();
        assertThat(home.isUseTLS()).isFalse();
    }

    @Test
    void nameServerWritesShouldReturnUnavailableWhenRuntimeIsDisabled() {
        OpsService opsService = service(runtime(false, "10.0.0.1:9876", new InMemoryRepository(), true),
                properties(false, "10.0.0.1:9876"));

        assertThatThrownBy(() -> opsService.addNameServer(" 10.0.0.1:9876 "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops settings are not connected to the cluster admin configuration")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
        assertThatThrownBy(() -> opsService.updateNameServer("10.0.0.2:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops settings are not connected to the cluster admin configuration")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
        assertThatThrownBy(() -> opsService.deleteNameServer("127.0.0.1:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops settings are not connected to the cluster admin configuration")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }

    @Test
    void togglesShouldReturnUnavailableWhenRuntimeIsDisabled() {
        OpsService opsService = service(runtime(false, "10.0.0.1:9876", new InMemoryRepository(), true),
                properties(false, "10.0.0.1:9876"));

        assertThatThrownBy(() -> opsService.updateVipChannel(false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops settings are not connected to the cluster admin configuration")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
        assertThatThrownBy(() -> opsService.updateUseTLS(true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops settings are not connected to the cluster admin configuration")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }

    @Test
    void enabledRuntimeShouldPersistNameServerSelectionAndRejectUnsafeMutations() {
        authenticateAdmin();
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "first:9876");
        OpsService opsService = service(new OpsRuntimeConnection(repository, properties, () -> false), properties);

        OpsHomeVO initial = opsService.getHomePage();
        assertThat(initial.isConfigurationAvailable()).isTrue();
        assertThat(initial.getNamesvrAddrList()).containsExactly("first:9876");
        assertThat(initial.getCurrentNamesrv()).isEqualTo("first:9876");

        opsService.addNameServer(" SECOND:9876 ");
        OpsHomeVO afterAdd = opsService.getHomePage();
        assertThat(afterAdd.getNamesvrAddrList()).containsExactly("first:9876", "second:9876");
        assertThat(afterAdd.getCurrentNamesrv()).isEqualTo("first:9876");

        opsService.updateNameServer("second:9876");
        assertThat(opsService.getHomePage().getCurrentNamesrv()).isEqualTo("second:9876");

        assertThatThrownBy(() -> opsService.addNameServer("second:9876"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> opsService.deleteNameServer("second:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete the selected NameServer address");
        assertThatThrownBy(() -> opsService.updateUseTLS(true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TLS requires");

        OpsHomeVO unchanged = opsService.getHomePage();
        assertThat(unchanged.getNamesvrAddrList()).containsExactly("first:9876", "second:9876");
        assertThat(unchanged.getCurrentNamesrv()).isEqualTo("second:9876");
        assertThat(unchanged.isUseTLS()).isFalse();

        opsService.updateNameServer("first:9876");
        opsService.deleteNameServer("second:9876");
        OpsHomeVO afterDelete = opsService.getHomePage();
        assertThat(afterDelete.getNamesvrAddrList()).containsExactly("first:9876");
        assertThat(afterDelete.getCurrentNamesrv()).isEqualTo("first:9876");
    }

    @Test
    void enabledRuntimeShouldPersistVipAndTlsWhenTlsPrerequisitesAreSecure() {
        authenticateAdmin();
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "first:9876");
        OpsService opsService = service(new OpsRuntimeConnection(repository, properties, () -> true), properties);

        opsService.updateVipChannel(true);
        opsService.updateUseTLS(true);

        OpsHomeVO home = opsService.getHomePage();
        assertThat(home.isUseVIPChannel()).isTrue();
        assertThat(home.isUseTLS()).isTrue();
    }

    @Test
    void enabledRuntimeShouldRejectDeletingTheLastAddress() {
        authenticateAdmin();
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, "first:9876");
        OpsService opsService = service(new OpsRuntimeConnection(repository, properties, () -> true), properties);

        assertThatThrownBy(() -> opsService.deleteNameServer("first:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cannot delete the last NameServer address");

        assertThat(opsService.getHomePage().getNamesvrAddrList()).containsExactly("first:9876");
    }

    @Test
    void enabledRuntimeWithBlankSeedShouldNotReportAvailableOrPersistTransportSettings() {
        authenticateAdmin();
        InMemoryRepository repository = new InMemoryRepository();
        OpsRuntimeProperties properties = properties(true, " ");
        OpsService opsService = service(new OpsRuntimeConnection(repository, properties, () -> true), properties);

        OpsHomeVO home = opsService.getHomePage();
        assertThat(home.isConfigurationAvailable()).isFalse();
        assertThat(home.getUnavailableReason()).isEqualTo("namesrvAddr is required");

        assertThatThrownBy(() -> opsService.updateVipChannel(true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required before transport settings can be updated");
        assertThat(repository.load()).isEmpty();

        opsService.addNameServer("ns1:9876");
        assertThat(opsService.getHomePage().isConfigurationAvailable()).isTrue();
    }

    @Test
    void nameServerOperationsShouldRejectBlankAddress() {
        authenticateAdmin();
        OpsService opsService = service(runtime(true, "10.0.0.1:9876", new InMemoryRepository(), true),
                properties(true, "10.0.0.1:9876"));

        assertThatThrownBy(() -> opsService.addNameServer(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> opsService.updateNameServer(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required");
        assertThatThrownBy(() -> opsService.deleteNameServer(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required");
    }

    @Test
    void runtimeWritesShouldRequireAuthenticatedAdministrator() {
        InMemoryRepository repository = new InMemoryRepository();
        OpsService opsService = service(runtime(true, "10.0.0.1:9876", repository, true),
                properties(true, "10.0.0.1:9876"));

        assertAdminRequired(() -> opsService.addNameServer("10.0.0.2:9876"));
        assertAdminRequired(() -> opsService.updateNameServer("10.0.0.1:9876"));
        assertAdminRequired(() -> opsService.deleteNameServer("10.0.0.1:9876"));
        assertAdminRequired(() -> opsService.updateVipChannel(true));
        assertAdminRequired(() -> opsService.updateUseTLS(true));
        assertThat(repository.load()).isEmpty();

        AuthenticatedUserContext.setUser("operator", false);
        assertAdminRequired(() -> opsService.addNameServer("10.0.0.2:9876"));
        assertThat(repository.load()).isEmpty();
    }

    private static OpsService service(OpsRuntimeConnection runtimeConnection, OpsRuntimeProperties properties) {
        return new OpsService(runtimeConnection, properties, mock(OperationAuditService.class));
    }

    private static void authenticateAdmin() {
        AuthenticatedUserContext.setUser("admin", true);
    }

    private static void assertAdminRequired(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ops runtime settings require an authenticated administrator")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(403));
    }

    private static OpsRuntimeConnection runtime(boolean enabled, String namesrvAddr,
                                                OpsConnectionRepository repository, boolean secureTls) {
        OpsRuntimeProperties properties = properties(enabled, namesrvAddr);
        return new OpsRuntimeConnection(repository, properties, () -> secureTls);
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
}
