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

import React from 'react';
import { Card, Space } from 'antd';
import { ChartLine } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import GrafanaDashboardList from '../../components/GrafanaDashboardList';
import ObservabilityAssetGuide from '../../components/ObservabilityAssetGuide';

const GrafanaDashboardsPage: React.FC = () => {
  const { t } = useLang();

  return (
    <div style={{ padding: '0 0 24px' }}>
      <Card
        size="small"
        title={
          <Space>
            <ChartLine size={18} />
            <span>{t('grafana.title')}</span>
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <ObservabilityAssetGuide kind="grafana" />
          <GrafanaDashboardList />
        </Space>
      </Card>
    </div>
  );
};

export default GrafanaDashboardsPage;
