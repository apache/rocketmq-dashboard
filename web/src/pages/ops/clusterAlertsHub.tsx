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

import { useState } from 'react';
import { Tabs } from 'antd';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import AlertsPage from './alerts';
import ClusterEventsTab from './clusterEvents';
import NotificationDeliveriesPage from './notificationDeliveries';
import SystemAlertsPage from './systemAlerts';

export type ClusterAlertsTabKey = 'rules' | 'events' | 'deliveries' | 'clusterEvents';

// 集群告警 / 告警事件 / 告警投递 / 集群事件 收进一个页面的四个 Tab（用户约定 2026-10-28），
// 侧边栏不再各占一项。子页以 embedded 形态渲染（无页头、无外层 padding）。
// destroyInactiveTabPane：切走即卸载，避免四个重列表同时挂着各自轮询与状态。
const ClusterAlertsHub = ({ initialTab = 'rules' }: { initialTab?: ClusterAlertsTabKey }) => {
  const { t } = useLang();
  const [activeKey, setActiveKey] = useState<ClusterAlertsTabKey>(initialTab);
  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('nav.alertRules')} subtitle={t('clusterAlerts.subtitle')} />
      <Tabs
        activeKey={activeKey}
        onChange={(key) => setActiveKey(key as ClusterAlertsTabKey)}
        destroyInactiveTabPane
        // 子页（告警规则 / 告警事件）把各自的页头操作区 portal 进这个容器，
        // 与页签同一行右对齐，不单独占一行。
        tabBarExtraContent={{
          right: <div id="cluster-alerts-tab-extra" style={{ display: 'inline-flex' }} />,
        }}
        items={[
          {
            key: 'rules',
            label: t('nav.alertRules'),
            children: <AlertsPage embedded />,
          },
          {
            key: 'events',
            label: t('nav.alertEvents'),
            children: <SystemAlertsPage embedded />,
          },
          {
            key: 'deliveries',
            label: t('nav.alertDeliveries'),
            children: <NotificationDeliveriesPage embedded />,
          },
          {
            key: 'clusterEvents',
            label: t('nav.clusterEvents'),
            children: <ClusterEventsTab />,
          },
        ]}
      />
    </div>
  );
};

export default ClusterAlertsHub;
