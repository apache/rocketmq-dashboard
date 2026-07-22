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

import React, { useRef, useState } from 'react';
import { App, Button, Input, Select, Space, Switch, Typography } from 'antd';
import { FloppyDisk, Plus } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import {
  addNameSvrAddr,
  queryOpsHomePage,
  updateIsVIPChannel,
  updateNameSvrAddr,
  updateUseTLS,
} from '../../api/ops';

const OpsPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [namesrvAddrList, setNamesrvAddrList] = useState<string[]>([]);
  const [selectedNamesrv, setSelectedNamesrv] = useState('');
  const [newNamesrvAddr, setNewNamesrvAddr] = useState('');
  const [useVIPChannel, setUseVIPChannel] = useState(false);
  const [useTLS, setUseTLS] = useState(false);
  const [writeOperationEnabled, setWriteOperationEnabled] = useState(true);

  // One-time initialization (ESLint-compliant: no useEffect+setState)
  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    const loadOpsData = async () => {
      try {
        const userRole = sessionStorage.getItem('userrole');
        setWriteOperationEnabled(userRole === null || userRole === '1');

        const data = await queryOpsHomePage();
        setNamesrvAddrList(data.namesvrAddrList);
        setUseVIPChannel(data.useVIPChannel);
        setUseTLS(data.useTLS);
        setSelectedNamesrv(data.currentNamesrv);
      } catch {
        message.error(t('ops.fetchFailed'));
      }
    };
    loadOpsData();
  }

  const handleUpdateNameSvrAddr = async () => {
    if (!selectedNamesrv) {
      message.warning(t('ops.selectNamesrv'));
      return;
    }
    try {
      await updateNameSvrAddr(selectedNamesrv);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    }
  };

  const handleAddNameSvrAddr = async () => {
    const addr = newNamesrvAddr.trim();
    if (!addr) {
      message.warning(t('ops.inputNamesrvAddr'));
      return;
    }
    try {
      await addNameSvrAddr(addr);
      if (!namesrvAddrList.includes(addr)) {
        setNamesrvAddrList([...namesrvAddrList, addr]);
      }
      setNewNamesrvAddr('');
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    }
  };

  const handleUpdateIsVIPChannel = async (checked: boolean) => {
    setUseVIPChannel(checked);
    try {
      await updateIsVIPChannel(checked);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
      setUseVIPChannel(!checked);
    }
  };

  const handleUpdateUseTLS = async (checked: boolean) => {
    setUseTLS(checked);
    try {
      await updateUseTLS(checked);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
      setUseTLS(!checked);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      {/* NameServer Address List */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.nameServerAddressList')}</Typography.Title>
        <Space wrap align="start">
          <Select
            style={{ minWidth: 400, maxWidth: 500 }}
            value={selectedNamesrv || undefined}
            onChange={setSelectedNamesrv}
            disabled={!writeOperationEnabled}
            placeholder={t('ops.selectNamesrv')}
            options={namesrvAddrList.map((addr) => ({ label: addr, value: addr }))}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={handleUpdateNameSvrAddr}
            >
              {t('common.update')}
            </Button>
          )}
          {writeOperationEnabled && (
            <Space.Compact>
              <Input
                style={{ width: 300 }}
                placeholder="NamesrvAddr"
                value={newNamesrvAddr}
                onChange={(e) => setNewNamesrvAddr(e.target.value)}
              />
              <Button type="primary" icon={<Plus size={16} />} onClick={handleAddNameSvrAddr}>
                {t('common.add')}
              </Button>
            </Space.Compact>
          )}
        </Space>
      </div>

      {/* VIP Channel */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.isUseVIPChannel')}</Typography.Title>
        <Space align="center">
          <Switch
            checked={useVIPChannel}
            onChange={handleUpdateIsVIPChannel}
            disabled={!writeOperationEnabled}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={() => handleUpdateIsVIPChannel(useVIPChannel)}
            >
              {t('common.update')}
            </Button>
          )}
        </Space>
      </div>

      {/* Use TLS */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.useTLS')}</Typography.Title>
        <Space align="center">
          <Switch
            checked={useTLS}
            onChange={handleUpdateUseTLS}
            disabled={!writeOperationEnabled}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={() => handleUpdateUseTLS(useTLS)}
            >
              {t('common.update')}
            </Button>
          )}
        </Space>
      </div>
    </div>
  );
};

export default OpsPage;
