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

import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Button, Result, Spin } from 'antd';
import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { getAuthStatus } from './api/auth';
import { isMockMode } from './services/dataMode';
import { useLang } from './i18n/LangContext';
import useAuthStore from './stores/authStore';
import MainLayout from './layouts/MainLayout';
import LoginPage from './pages/login';

const HomePage = lazy(() => import('./pages/home'));
const InstancePage = lazy(() => import('./pages/instance'));
const TopicPage = lazy(() => import('./pages/instance/topic'));
const ConsumerPage = lazy(() => import('./pages/instance/consumer'));
const MessagePage = lazy(() => import('./pages/instance/message'));
const AclPage = lazy(() => import('./pages/instance/acl'));
const DlqPage = lazy(() => import('./pages/instance/dlq'));
const ClusterPage = lazy(() => import('./pages/cluster'));
const K8sCertsPage = lazy(() => import('./pages/cluster/certs'));
const ClientsPage = lazy(() => import('./pages/cluster/clients'));
const DashboardOpsPage = lazy(() => import('./pages/home/dashboard'));
const AlertsPage = lazy(() => import('./pages/ops/alerts'));
const SystemAlertsPage = lazy(() => import('./pages/ops/systemAlerts'));
const AuditPage = lazy(() => import('./pages/ops/audit'));
const NameServerConfigDriftPage = lazy(() => import('./pages/ops/nameServerConfigDrift'));
const AiPage = lazy(() => import('./pages/ai'));
const SettingsPage = lazy(() => import('./pages/settings'));
const LlmSettingsPage = lazy(() => import('./pages/studio/LlmSettings'));
const ProxyPage = lazy(() => import('./pages/studio/Proxy'));
const LiteTopicPage = lazy(() => import('./pages/studio/LiteTopic'));
const GroupManagementPage = lazy(() => import('./pages/studio/GroupManagement'));
const BrokerClusterPage = lazy(() => import('./pages/studio/BrokerCluster'));
const SslSettingsPage = lazy(() => import('./pages/studio/SslSettings'));
const GrafanaDashboardsPage = lazy(() => import('./pages/studio/GrafanaDashboards'));
const ProducerPage = lazy(() => import('./pages/studio/Producer'));
const OpsPage = lazy(() => import('./pages/studio/Ops'));
const AlertRuleAssetsPage = lazy(() => import('./pages/studio/AlertRuleAssets'));

type AuthGateState = 'checking' | 'allowed' | 'denied' | 'error';

export function AuthGate() {
  const { t } = useLang();
  const clearAuth = useAuthStore((state) => state.logout);
  const [gateState, setGateState] = useState<AuthGateState>(isMockMode() ? 'allowed' : 'checking');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (isMockMode()) return;

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

export function LazyRouteOutlet() {
  const { t } = useLang();

  return (
    <Suspense
      fallback={
        <div
          role="status"
          aria-label={t('common.loading')}
          style={{ minHeight: 240, display: 'grid', placeItems: 'center' }}
        >
          <Spin size="large" />
        </div>
      }
    >
      <Outlet />
    </Suspense>
  );
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AuthGate />}>
        <Route path="/" element={<MainLayout />}>
          <Route element={<LazyRouteOutlet />}>
            <Route index element={<HomePage />} />
            <Route path="instance" element={<InstancePage />} />
            <Route path="instance/topic" element={<TopicPage />} />
            <Route path="instance/:instanceId/topic" element={<TopicPage />} />
            <Route path="instance/consumer" element={<ConsumerPage />} />
            <Route path="instance/:instanceId/consumer" element={<ConsumerPage />} />
            <Route path="instance/message" element={<MessagePage />} />
            <Route path="instance/:instanceId/message" element={<MessagePage />} />
            <Route path="instance/acl" element={<AclPage />} />
            <Route path="instance/:instanceId/acl" element={<AclPage />} />
            <Route path="instance/dlq" element={<DlqPage />} />
            <Route path="instance/:instanceId/dlq" element={<DlqPage />} />
            <Route path="cluster" element={<ClusterPage />} />
            <Route path="cluster/certs" element={<K8sCertsPage />} />
            <Route path="cluster/clients" element={<ClientsPage />} />
            <Route path="ops/dashboard" element={<DashboardOpsPage />} />
            <Route path="ops/grafana" element={<GrafanaDashboardsPage />} />
            <Route path="ops/alerts" element={<AlertsPage />} />
            <Route path="ops/system-alerts" element={<SystemAlertsPage />} />
            <Route path="ops/audit" element={<AuditPage />} />
            <Route path="ops/nameserver-config-drift" element={<NameServerConfigDriftPage />} />
            <Route path="ai" element={<AiPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="studio/llm-settings" element={<LlmSettingsPage />} />
            <Route path="studio/proxy" element={<ProxyPage />} />
            <Route path="studio/lite-topic" element={<LiteTopicPage />} />
            <Route path="studio/group-management" element={<GroupManagementPage />} />
            <Route path="studio/broker-cluster" element={<BrokerClusterPage />} />
            <Route path="studio/ssl-settings" element={<SslSettingsPage />} />
            <Route path="studio/alert-management" element={<Navigate to="/ops/alerts" replace />} />
            <Route path="studio/producer" element={<ProducerPage />} />
            <Route path="studio/ops" element={<OpsPage />} />
            <Route path="ops/alert-rule-templates" element={<AlertRuleAssetsPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
