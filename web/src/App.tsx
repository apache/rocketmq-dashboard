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

import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import HomePage from './pages/home';
import InstancePage from './pages/instance';
import TopicPage from './pages/instance/topic';
import ConsumerPage from './pages/instance/consumer';
import MessagePage from './pages/instance/message';
import AclPage from './pages/instance/acl';
import DlqPage from './pages/instance/dlq';
import ClusterPage from './pages/cluster';
import K8sCertsPage from './pages/cluster/certs';
import ClientsPage from './pages/cluster/clients';
import DashboardOpsPage from './pages/home/dashboard';
import AlertsPage from './pages/ops/alerts';
import SystemAlertsPage from './pages/ops/systemAlerts';
import AuditPage from './pages/ops/audit';
import AiPage from './pages/ai';
import SettingsPage from './pages/settings';
import BrokerClusterPage from './pages/studio/BrokerCluster';

function App() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="instance" element={<InstancePage />} />
        <Route path="instance/topic" element={<TopicPage />} />
        <Route path="instance/consumer" element={<ConsumerPage />} />
        <Route path="instance/message" element={<MessagePage />} />
        <Route path="instance/acl" element={<AclPage />} />
        <Route path="instance/dlq" element={<DlqPage />} />
        <Route path="cluster" element={<ClusterPage />} />
        <Route path="cluster/certs" element={<K8sCertsPage />} />
        <Route path="cluster/clients" element={<ClientsPage />} />
        <Route path="ops/dashboard" element={<DashboardOpsPage />} />
        <Route path="ops/alerts" element={<AlertsPage />} />
        <Route path="ops/system-alerts" element={<SystemAlertsPage />} />
        <Route path="ops/audit" element={<AuditPage />} />
        <Route path="ai" element={<AiPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="studio/broker-cluster" element={<BrokerClusterPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default App;
