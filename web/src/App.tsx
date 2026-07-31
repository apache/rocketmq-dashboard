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

import { useCallback, useEffect, useState } from 'react';
import { Button, Result, Spin } from 'antd';
import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { getAuthStatus } from './api/auth';
import { USE_MOCK } from './config';
import { useLang } from './i18n/LangContext';
import useAuthStore from './stores/authStore';
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
import LlmSettingsPage from './pages/studio/LlmSettings';
import ProxyPage from './pages/studio/Proxy';
import LiteTopicPage from './pages/studio/LiteTopic';
import GroupManagementPage from './pages/studio/GroupManagement';
import BrokerClusterPage from './pages/studio/BrokerCluster';
import SslSettingsPage from './pages/studio/SslSettings';
import AlertManagementPage from './pages/studio/AlertManagement';
import ProducerPage from './pages/studio/Producer';
import OpsPage from './pages/studio/Ops';
import LoginPage from './pages/login';

type AuthGateState = 'checking' | 'allowed' | 'denied' | 'error';

export function AuthGate() {
  const { t } = useLang();
  const clearAuth = useAuthStore((state) => state.logout);
  const [gateState, setGateState] = useState<AuthGateState>(USE_MOCK ? 'allowed' : 'checking');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (USE_MOCK) return;

    let cancelled = false;
    void getAuthStatus()
      .then((status) => {
        if (cancelled) return;
        if (!status.loginRequired || status.authenticated) {
          setGateState('allowed');
          return;
        }
        clearAuth();
        setGateState('denied');
      })
      .catch(() => {
        if (!cancelled) setGateState('error');
      });

    return () => {
      cancelled = true;
    };
  }, [attempt, clearAuth]);

  const retry = useCallback(() => {
    setGateState('checking');
    setAttempt((current) => current + 1);
  }, []);

  if (gateState === 'checking') {
    return (
      <div
        role="status"
        aria-label={t('common.loading')}
        style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}
      >
        <Spin size="large" />
      </div>
    );
  }
  if (gateState === 'denied') return <Navigate to="/login" replace />;
  if (gateState === 'error') {
    return (
      <Result
        status="error"
        title={t('login.statusCheckFailed')}
        extra={
          <Button type="primary" onClick={retry}>
            {t('common.retry')}
          </Button>
        }
      />
    );
  }
  return <Outlet />;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AuthGate />}>
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
          <Route path="studio/llm-settings" element={<LlmSettingsPage />} />
          <Route path="studio/proxy" element={<ProxyPage />} />
          <Route path="studio/lite-topic" element={<LiteTopicPage />} />
          <Route path="studio/group-management" element={<GroupManagementPage />} />
          <Route path="studio/broker-cluster" element={<BrokerClusterPage />} />
          <Route path="studio/ssl-settings" element={<SslSettingsPage />} />
          <Route path="studio/alert-management" element={<AlertManagementPage />} />
          <Route path="studio/producer" element={<ProducerPage />} />
          <Route path="studio/ops" element={<OpsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
