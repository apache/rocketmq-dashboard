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
package org.apache.rocketmq.studio.cluster.broker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the retirement protocol of {@link ClientLease}: a cached client is retired without being
 * shut down while operations are still in flight, the shutdown runs once the last holder releases,
 * and it runs exactly once however often retirement or release is repeated.
 */
class ClientLeaseTest {

    private final List<String> shutdownClients = new ArrayList<>();

    private ClientLease<String> newLease() {
        return new ClientLease<>("client-a", shutdownClients::add);
    }

    @Test
    void acquireIsRefusedOnceRetired() {
        ClientLease<String> lease = newLease();
        assertThat(lease.acquire()).isTrue();
        lease.retire();
        assertThat(lease.acquire()).isFalse();
    }

    @Test
    void retiringAnIdleLeaseShutsTheClientDownImmediately() {
        newLease().retire();
        assertThat(shutdownClients).containsExactly("client-a");
    }

    @Test
    void retireDefersShutdownUntilTheLastHolderReleases() {
        ClientLease<String> lease = newLease();
        lease.acquire();
        lease.retire();
        assertThat(shutdownClients).isEmpty();
        lease.release();
        assertThat(shutdownClients).containsExactly("client-a");
    }

    @Test
    void releaseWithoutRetireKeepsTheClientOpen() {
        ClientLease<String> lease = newLease();
        lease.acquire();
        lease.release();
        assertThat(shutdownClients).isEmpty();
        assertThat(lease.acquire()).isTrue();
    }

    @Test
    void repeatedReleasesShutTheClientDownExactlyOnce() {
        ClientLease<String> lease = newLease();
        lease.acquire();
        lease.acquire();
        lease.retire();
        lease.release();
        lease.release();
        assertThat(shutdownClients).containsExactly("client-a");
    }

    @Test
    void repeatedRetirementShutsTheClientDownExactlyOnce() {
        ClientLease<String> lease = newLease();
        lease.retire();
        lease.retire();
        assertThat(shutdownClients).containsExactly("client-a");
    }

    @Test
    void clientExposesTheWrappedInstance() {
        assertThat(newLease().client()).isEqualTo("client-a");
    }
}
