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

import { Alert, Card, Space } from 'antd';
import { ShieldCheck } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

const SslSettingsPage = () => {
  const { t } = useLang();

  return (
    <div style={{ padding: 0 }}>
      <Card
        title={
          <Space>
            <ShieldCheck size={18} style={{ color: '#1677ff' }} />
            <span>{t('ssl.title')}</span>
          </Space>
        }
        bordered={false}
        style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}
      >
        <Alert
          message={t('ssl.unavailable')}
          description={t('ssl.unavailableDesc')}
          type="warning"
          showIcon
          data-testid="ssl-settings-unavailable"
        />
      </Card>
    </div>
  );
};

export default SslSettingsPage;
