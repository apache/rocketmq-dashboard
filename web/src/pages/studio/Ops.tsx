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

import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tooltip,
  Typography,
} from 'antd';
import { FloppyDisk, Plus, Trash } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import useAuthStore from '../../stores/authStore';
import {
  addNameSvrAddr,
  deleteNameSvrAddr,
  queryOpsHomePage,
  updateIsVIPChannel,
  updateNameSvrAddr,
  updateUseTLS,
} from '../../api/ops';

const OpsPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const fetchFailedMessage = t('ops.fetchFailed');
  const userId = useAuthStore((state) => state.userId);
  const admin = useAuthStore((state) => state.admin);

  const [namesrvAddrList, setNamesrvAddrList] = useState<string[]>([]);
  const [selectedNamesrv, setSelectedNamesrv] = useState('');
  const [currentNamesrv, setCurrentNamesrv] = useState('');
  const [newNamesrvAddr, setNewNamesrvAddr] = useState('');
  const [useVIPChannel, setUseVIPChannel] = useState(false);
  const [useTLS, setUseTLS] = useState(false);
  const [namesrvUpdating, setNamesrvUpdating] = useState(false);
  const [vipUpdating, setVipUpdating] = useState(false);
  const [tlsUpdating, setTlsUpdating] = useState(false);
  const namesrvMutationInFlight = useRef(false);
  const vipUpdateInFlight = useRef(false);
  const tlsUpdateInFlight = useRef(false);
  const [configurationAvailable, setConfigurationAvailable] = useState(false);
  const [unavailableReason, setUnavailableReason] = useState('');
  const writeOperationEnabled = configurationAvailable && (!userId || admin === true);
  const deleteNameServerDisabled =
    !selectedNamesrv || selectedNamesrv === currentNamesrv || namesrvAddrList.length <= 1;

  useEffect(() => {
    let cancelled = false;

    const loadOpsData = async () => {
      try {
        const data = await queryOpsHomePage();
        if (!cancelled) {
          setNamesrvAddrList(data.namesvrAddrList);
          setUseVIPChannel(data.useVIPChannel);
          setUseTLS(data.useTLS);
          setSelectedNamesrv(data.currentNamesrv);
          setCurrentNamesrv(data.currentNamesrv);
          setConfigurationAvailable(data.configurationAvailable);
          setUnavailableReason(data.unavailableReason || '');
        }
      } catch {
        if (!cancelled) {
          message.error(fetchFailedMessage);
        }
      }
    };

    void loadOpsData();

    return () => {
      cancelled = true;
    };
  }, [fetchFailedMessage, message]);

  const handleUpdateNameSvrAddr = async () => {
    if (namesrvMutationInFlight.current) return;
    if (!selectedNamesrv) {
      message.warning(t('ops.selectNamesrv'));
      return;
    }
    namesrvMutationInFlight.current = true;
    setNamesrvUpdating(true);
    try {
      await updateNameSvrAddr(selectedNamesrv);
      setCurrentNamesrv(selectedNamesrv);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    } finally {
      namesrvMutationInFlight.current = false;
      setNamesrvUpdating(false);
    }
  };

  const handleDeleteNameSvrAddr = async () => {
    if (namesrvMutationInFlight.current) return;
    namesrvMutationInFlight.current = true;
    setNamesrvUpdating(true);
    try {
      await deleteNameSvrAddr(selectedNamesrv);
      setNamesrvAddrList((addresses) => addresses.filter((addr) => addr !== selectedNamesrv));
      setSelectedNamesrv(currentNamesrv);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    } finally {
      namesrvMutationInFlight.current = false;
      setNamesrvUpdating(false);
    }
  };

  const handleAddNameSvrAddr = async () => {
    if (namesrvMutationInFlight.current) return;
    const addr = newNamesrvAddr.trim();
    if (!addr) {
      message.warning(t('ops.inputNamesrvAddr'));
      return;
    }
    namesrvMutationInFlight.current = true;
    setNamesrvUpdating(true);
    try {
      await addNameSvrAddr(addr);
      if (!namesrvAddrList.includes(addr)) {
        setNamesrvAddrList([...namesrvAddrList, addr]);
      }
      setNewNamesrvAddr('');
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    } finally {
      namesrvMutationInFlight.current = false;
      setNamesrvUpdating(false);
    }
  };

  const handleUpdateIsVIPChannel = async (checked: boolean) => {
    if (vipUpdateInFlight.current) return;
    vipUpdateInFlight.current = true;
    setVipUpdating(true);
    setUseVIPChannel(checked);
    try {
      await updateIsVIPChannel(checked);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
      setUseVIPChannel(!checked);
    } finally {
      vipUpdateInFlight.current = false;
      setVipUpdating(false);
    }
  };

  const handleUpdateUseTLS = async (checked: boolean) => {
    if (tlsUpdateInFlight.current) return;
    tlsUpdateInFlight.current = true;
    setTlsUpdating(true);
    setUseTLS(checked);
    try {
      await updateUseTLS(checked);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
      setUseTLS(!checked);
    } finally {
      tlsUpdateInFlight.current = false;
      setTlsUpdating(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      {!configurationAvailable && (
        <Alert
          type="info"
          showIcon
          message={t('ops.runtimeUnavailable')}
          description={unavailableReason || t('ops.runtimeUnavailableDefault')}
          style={{ marginBottom: 24 }}
        />
      )}
      {/* NameServer Address List */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.nameServerAddressList')}</Typography.Title>
        <Space wrap align="start">
          <Select
            style={{ minWidth: 400, maxWidth: 500 }}
            value={selectedNamesrv || undefined}
            onChange={setSelectedNamesrv}
            disabled={!writeOperationEnabled || namesrvUpdating}
            placeholder={t('ops.selectNamesrv')}
            options={namesrvAddrList.map((addr) => ({ label: addr, value: addr }))}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={handleUpdateNameSvrAddr}
              loading={namesrvUpdating}
              disabled={namesrvUpdating}
            >
              {t('common.update')}
            </Button>
          )}
          {writeOperationEnabled && (
            <Popconfirm
              title={t('common.areYouSureToDelete')}
              onConfirm={handleDeleteNameSvrAddr}
              okText={t('common.confirm')}
              cancelText={t('common.cancel')}
              disabled={deleteNameServerDisabled || namesrvUpdating}
            >
              <Tooltip title={t('common.delete')}>
                <Button
                  danger
                  aria-label={t('common.delete')}
                  icon={<Trash size={16} />}
                  disabled={deleteNameServerDisabled || namesrvUpdating}
                />
              </Tooltip>
            </Popconfirm>
          )}
          {writeOperationEnabled && (
            <Space.Compact>
              <Input
                style={{ width: 300 }}
                placeholder="NamesrvAddr"
                value={newNamesrvAddr}
                onChange={(e) => setNewNamesrvAddr(e.target.value)}
                disabled={namesrvUpdating}
              />
              <Button
                type="primary"
                icon={<Plus size={16} />}
                onClick={handleAddNameSvrAddr}
                loading={namesrvUpdating}
                disabled={namesrvUpdating}
              >
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
            disabled={!writeOperationEnabled || vipUpdating}
            loading={vipUpdating}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={() => handleUpdateIsVIPChannel(useVIPChannel)}
              loading={vipUpdating}
              disabled={vipUpdating}
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
            disabled={!writeOperationEnabled || tlsUpdating}
            loading={tlsUpdating}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={() => handleUpdateUseTLS(useTLS)}
              loading={tlsUpdating}
              disabled={tlsUpdating}
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
