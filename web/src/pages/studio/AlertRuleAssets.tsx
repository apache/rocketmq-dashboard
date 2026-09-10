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
import { Card } from 'antd';
import { useLang } from '../../i18n/LangContext';
import PageHeader from '../../components/PageHeader';
import AlertRuleAssetList from '../../components/AlertRuleAssetList';

const AlertRuleAssetsPage: React.FC = () => {
  const { t } = useLang();

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('alertAssets.title')} subtitle={t('alertAssets.subtitle')} />
      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ padding: 16 }}>
          <AlertRuleAssetList />
        </div>
      </Card>
    </div>
  );
};

export default AlertRuleAssetsPage;
