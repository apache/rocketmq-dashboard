/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQDefaultClusterResolverTest {

    private RocketMQProperties properties;
    private MqAdminProperties adminProperties;
    private MqAdminExtFactory adminFactory;
    private RocketMQDefaultClusterResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new RocketMQProperties();
        adminProperties = new MqAdminProperties();
        adminFactory = mock(MqAdminExtFactory.class);
        resolver = new RocketMQDefaultClusterResolver(properties, adminProperties, adminFactory);
    }

    private void configuredCluster() {
        properties.setNamesrvAddr(" 127.0.0.1:9876 ");
    }

    private void configuredAdmin() {
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey(" ak ");
        credential.setSecretKey(" sk ");
        adminProperties.getCredentials().put("admin", credential);
    }

    @Test
    void findIsEmptyWithoutNamesrvOrClusterName() {
        assertThat(resolver.find("DefaultCluster")).isEmpty();

        configuredCluster();
        assertThat(resolver.find(null)).isEmpty();
        assertThat(resolver.find("  ")).isEmpty();
    }

    @Test
    void namesWithoutNamesrvAreEmptyWithoutTouchingTheAdminFactory() {
        assertThat(resolver.names()).isEmpty();
        verify(adminFactory, org.mockito.Mockito.never())
                .execute(any(), any(), any(MqAdminExtFactory.AdminAction.class));
    }

    @Test
    void namesDiscoverAnonymouslyAndSortTheClusterTable() throws Exception {
        configuredCluster();
        org.apache.rocketmq.tools.admin.DefaultMQAdminExt admin =
                mock(org.apache.rocketmq.tools.admin.DefaultMQAdminExt.class);
        org.apache.rocketmq.remoting.protocol.body.ClusterInfo info =
                new org.apache.rocketmq.remoting.protocol.body.ClusterInfo();
        java.util.HashMap<String, java.util.Set<String>> table = new java.util.HashMap<>();
        table.put("Beta", java.util.Set.of("b1"));
        table.put("Alpha", java.util.Set.of("a1"));
        info.setClusterAddrTable(table);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);
        when(adminFactory.execute(any(), isNull(), any(MqAdminExtFactory.AdminAction.class)))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2)
                        .apply(admin));

        List<String> names = resolver.names();

        assertThat(names).containsExactly("Alpha", "Beta");
        verify(adminFactory).execute(eq("127.0.0.1:9876"), isNull(),
                any(MqAdminExtFactory.AdminAction.class));
    }

    @Test
    void namesSurviveAnEmptyClusterTable() throws Exception {
        configuredCluster();
        org.apache.rocketmq.tools.admin.DefaultMQAdminExt admin =
                mock(org.apache.rocketmq.tools.admin.DefaultMQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        when(adminFactory.execute(any(), isNull(), any(MqAdminExtFactory.AdminAction.class)))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2)
                        .apply(admin));

        assertThat(resolver.names()).isEmpty();
    }

    @Test
    void anIncompleteAdminCredentialIsTreatedAsUnconfigured() {
        configuredCluster();
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey("ak");
        // secret key blank: the credential is unusable
        adminProperties.getCredentials().put("admin", credential);
        when(adminFactory.execute(any(), isNull(), any(MqAdminExtFactory.AdminAction.class)))
                .thenReturn(List.of("DefaultCluster"));

        assertThat(resolver.names()).containsExactly("DefaultCluster");
    }

    @Test
    void findReturnsADirectApacheInstanceForAKnownCluster() {
        configuredCluster();
        configuredAdmin();
        when(adminFactory.execute(any(), any(RPCHook.class), eq("admin"),
                any(MqAdminExtFactory.AdminAction.class)))
                .thenReturn(List.of("DefaultCluster"));

        Optional<InstanceVO> found = resolver.find("DefaultCluster");

        assertThat(found).isPresent();
        InstanceVO instance = found.get();
        assertThat(instance.getName()).isEqualTo("DefaultCluster");
        assertThat(instance.getEndpoint()).isEqualTo("127.0.0.1:9876");
        assertThat(instance.getVendor().name()).isEqualTo("APACHE");
        assertThat(instance.getType().name()).isEqualTo("DIRECT");
        assertThat(instance.getAdminCredentialRef()).isEqualTo("admin");
    }

    @Test
    void instanceAdvertisesNoCredentialRefWhenAclIsNotConfigured() {
        configuredCluster();
        when(adminFactory.execute(any(), isNull(), any(MqAdminExtFactory.AdminAction.class)))
                .thenReturn(List.of("DefaultCluster"));

        Optional<InstanceVO> found = resolver.find("DefaultCluster");

        assertThat(found).isPresent();
        assertThat(found.get().getAdminCredentialRef()).isNull();
    }

    @Test
    void executeFailsClosedWithoutAnAdminCredential() {
        configuredCluster();

        assertThatThrownBy(() -> resolver.execute(action -> null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin credential reference is not configured");
    }

    @Test
    void instanceFailsWithServiceUnavailableWithoutNamesrv() {
        assertThatThrownBy(() -> resolver.instance("DefaultCluster"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RocketMQ admin not connected");
    }
}
