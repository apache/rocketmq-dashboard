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

import { Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';

import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { GeneralSettingsTab } from './GeneralSettingsTab';
import { AiAssistantTab } from './AiAssistantTab';
import { CloudCredentialTab } from './CloudCredentialTab';
import { DataSourceTab } from './DataSourceTab';
import { AboutTab } from './AboutTab';

const TAB_KEYS = ['general', 'ai', 'credential', 'datasource', 'about'] as const;
type TabKey = (typeof TAB_KEYS)[number];

const SettingsPage = () => {
  const { t } = useLang();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const activeKey: TabKey = TAB_KEYS.includes(tabParam as TabKey)
    ? (tabParam as TabKey)
    : 'general';

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />

      <Tabs
        activeKey={activeKey}
        onChange={(key) =>
          setSearchParams((current) => {
            const next = new URLSearchParams(current);
            next.set('tab', key);
            return next;
          })
        }
        items={[
          { key: 'general', label: t('settings.tabGeneral'), children: <GeneralSettingsTab /> },
          { key: 'ai', label: t('settings.tabAi'), children: <AiAssistantTab /> },
          {
            key: 'credential',
            label: t('settings.tabCredential'),
            children: <CloudCredentialTab />,
          },
          { key: 'datasource', label: t('settings.tabDatasource'), children: <DataSourceTab /> },
          { key: 'about', label: t('settings.tabAbout'), children: <AboutTab /> },
        ]}
      />
    </div>
  );
};

export default SettingsPage;
